/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos

import kotlin.system.exitProcess
import atropos.bridge.LocalEngineBridge
import atropos.cli.BackgroundCommandRunner
import atropos.cli.CommandRouter
import atropos.cli.RouterOutcome
import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandCompleter
import atropos.cli.input.CommandHistoryStore
import atropos.cli.input.KeyEvent
import atropos.cli.input.PromptEffect
import atropos.cli.input.PromptState
import atropos.cli.input.RawKeyReader
import atropos.cli.input.TerminalModeManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.ScreenId
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.HomeStateProvider
import atropos.cli.ui.ViewportLayout
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.agent.SelfHostStartupContinuationService
import atropos.core.agent.AgentDaemonService
import atropos.core.auth.AuthBootstrap
import atropos.core.provider.ProviderDaemon
import atropos.core.provider.ProviderHealthReport
import atropos.core.recovery.RuntimeContinuitySupervisor
import atropos.core.recovery.CrashRecoveryService
import atropos.core.recovery.StartupContinuationDecider
import atropos.core.recovery.ContinuityOutcome
import atropos.core.security.CredentialBootstrap
import atropos.core.security.CredentialVault
import atropos.core.security.RedactionFilter
import atropos.core.security.SecretEnrollment
import atropos.core.security.EnvironmentSecretSource
import atropos.core.security.LocalVaultSecretSource
import java.io.FileInputStream

const val ATROPOS_HEALTH_MARKER = "ATROPOS_HEALTHY"

fun main(args: Array<String>) {
    // ALPHA-OMEGA BOOTSTRAP: Immutable credential injection BEFORE anything else
    val credentialVault = CredentialBootstrap.initialize()
    atropos.core.provider.FallbackChainRegistry.loadFromEnvironmentAndVault()
    atropos.core.provider.ModelDiscoveryEngine.discoverAndPrune()

    val enrollment = SecretEnrollment(listOf(EnvironmentSecretSource(), LocalVaultSecretSource()))
        .enrollInto(RedactionFilter.defaultRegistry)
    if (enrollment.failures.isNotEmpty()) {
        println("${enrollment.evidenceLine()} status=DEGRADED")
    }

    // Answered before anything else boots.
    //
    // A version flag that needed a working config, a provider registry and an
    // authority gate to answer would be unable to report the one thing an
    // operator asks it for when something is wrong.
    if (args.firstOrNull() == "--version" || args.firstOrNull() == "-v") {
        println(atropos.core.BuildStamp.line())
        return
    }

    if (args.firstOrNull() == "--help" || args.firstOrNull() == "-h" || args.firstOrNull() == "help") {
        println(atropos.cli.help.HelpGenerator().render(atropos.cli.help.HelpLevel.SUMMARY).joinToString("\n"))
        return
    }

    if (args.firstOrNull() == "doctor" && args.drop(1).any { it == "--version" || it == "-v" }) {
        println(atropos.core.BuildStamp.line())
        return
    }

    // Answered before the config boots, like --version.
    //
    // An install too broken to start is exactly the install that needs
    // replacing, so the updater must not depend on anything the update might
    // be fixing.
    if (args.firstOrNull() == "update" || args.firstOrNull() == "--update") {
        val outcome = atropos.core.SelfUpdate().update()
        println(atropos.core.SelfUpdateText.render(outcome))
        return
    }

    if (args.firstOrNull() == "--health") {
        println(ATROPOS_HEALTH_MARKER)
        return
    }

    if (args.any { it == "--systemic-seal" }) {
        val gate = atropos.core.doctor.SystemicSealAuditGate(java.nio.file.Path.of("."))
        val passed = gate.verifySeal()
        if (passed) {
            println("SEAL_STATUS = THE_ARCHITECTURE_IS_DEFINITIVELY_COMPLETE_AND_CLOSED")
            return
        } else {
            println("SEAL_STATUS = SEAL_VERIFICATION_FAILED")
            exitProcess(1)
        }
    }

    // ALPHA-OMEGA: ProviderDaemon - synchronous blocking health validation at boot time
    val config = AtroposConfig.load()
    val providerOnboarding = atropos.core.provider.ProviderOnboardingService()
    val providerDaemon = ProviderDaemon(CredentialBootstrap.get(), onboarding = providerOnboarding, config = config)
    val healthReport = providerDaemon.validateAndSync()

    // Discovery is cheap and metadata-only. Construct one owner for every
    // launch mode, including the daemon foreground entrypoint.
    providerOnboarding.refresh()

    if (args.firstOrNull() == "--agent-daemon-foreground") {
        val config = AtroposConfig.load()
        val result = AgentDaemonService(config, onboarding = providerOnboarding)
            .foreground(config.runtime.defaultProvider)
        println(result.render())
        return
    }

    val capabilities = ConfigurationManager()
    val ui = AnsiTerminalEngine(capabilities)
    val tracker = QuotaSessionTracker()

    try {
        // Pass the one launch owner to every downstream router/service; no
        // second environment scan is permitted during this process.
        ui.renderNotice(healthReport.message)
        ui.renderNotice(providerOnboarding.renderLaunchSummary(refresh = false))
        if (!healthReport.overallHealthy) {
            ui.renderNotice("No healthy providers. Run '/provider connect' to configure API keys.")
        }

        if (args.firstOrNull() == "--doctor" || args.firstOrNull() == "doctor") {
            if (args.any { it == "--factory-completeness" }) {
                val gate = atropos.core.doctor.CallerCompletenessGate(java.nio.file.Path.of("."))
                val probe = atropos.core.doctor.CallerCompletenessDoctorProbe(gate)
                println(probe.check())
                return
            }
            if (args.any { it == "--tier4-completeness" }) {
                val gate = atropos.core.doctor.CallerCompletenessGate(java.nio.file.Path.of("."))
                val probe = atropos.core.doctor.CallerCompletenessDoctorProbe(gate)
                println(probe.checkTier4())
                return
            }
            if (args.any { it == "--orphans" || it == "--symbol-orphans" }) {
                val orphanDoctor = atropos.core.doctor.UiSymbolOrphanDoctor(java.nio.file.Path.of("."))
                val report = orphanDoctor.auditUiCallers()
                println("Scanned symbols: ${report.totalSymbolsScanned}, Orphans: ${report.orphanSymbols.size}")
                report.orphanSymbols.take(50).forEach { println("  $it") }
                return
            }
            atropos.cli.FirstRunDoctorRenderer(
                backendDoctor = atropos.cli.BackendDoctor(config, providerOnboarding),
                homeState = HomeStateProvider()
            ).render(config.runtime.defaultProvider).forEach(::println)
            return
        }

        // Repair is automatic; resuming is not.
        //
        // Durable state left by a previous process is still repaired before the
        // runtime serves anything — a stale lease or an unmarked crashed run is
        // worse left alone. But continuing that work used to happen here too,
        // which meant opening ATROPOS could find it already acting on something
        // from a previous session that nobody asked for now. Startup reports
        // what is resumable and stops; `/agent self-host recover` resumes it.
        // ATROPOS_AUTO_CONTINUE=1 restores the old behaviour for unattended
        // runners, where no one is present to type the command.
        // Authority documents are attested before anything can be dispatched.
        // A tampered AGENTS.md is an instruction set nobody authorised, and it
        // has to be caught here rather than noticed later in output that looks
        // subtly wrong.
        val authority = AuthBootstrap().boot()

        val continuity = RuntimeContinuitySupervisor {
            CrashRecoveryService(config = config, onboarding = providerOnboarding).recover()
        }
        val continuityOutcome = continuity.ensureRecovered()
        continuity.startupNotice(continuityOutcome)?.let(ui::renderNotice)

        val continuation = StartupContinuationDecider()
            .decide(continuityOutcome.safeForSelfHostContinuation)
        continuation.message?.let(ui::renderNotice)
        if (continuation.continued) {
            SelfHostStartupContinuationService(onboarding = providerOnboarding)
                .continueOnce(true)
                .takeIf { it.attempted }
                ?.let { result ->
                    if (result.ok) ui.renderNotice(result.message ?: "self-host continuation completed")
                    else ui.renderError(result.message ?: "self-host continuation stopped")
                }
        }

        val mcpHostManager = atropos.core.integration.McpHostManager(
            AtroposRepoRootLocator.resolve(),
            localOnly = config.runtime.localOnly
        )
        val router = CommandRouter(
            config = config,
            uiEngine = ui,
            sessionTracker = tracker,
            rateResolver = capabilities::inputUsdPerToken,
            providerOnboarding = providerOnboarding,
            providerDiscoveryAlreadyRefreshed = true,
            sharedMcpHostManager = mcpHostManager
        )

        // The engine's client-facing listener. Off unless the operator asks for
        // it: Source Doc 4 makes Web and Android clients of this engine, but a
        // port that opens on every start is a surface nobody chose to expose.
        // Loopback-bound and read-only regardless — see BridgeRoutes.
        val bridge = LocalEngineBridge.fromEnvironment(
            activeProvider = { config.runtime.defaultProvider },
            onboarding = providerOnboarding,
            mcpHostManager = mcpHostManager
        )
        bridge?.let { server ->
            if (server.start()) {
                ui.renderNotice("bridge listening on 127.0.0.1:${server.boundPort()} (read-only)")
            } else {
                ui.renderError("bridge failed to start: ${server.lastError() ?: "unknown"}")
            }
        }

        // `atropos auth accept AGENTS.md` -- argv as one command, then exit.
        //
        // This did not exist. `main` read argv for exactly two flags and threw
        // the rest away, so every `atropos <something>` booted the CLI and
        // ignored what it was asked to do. The visible consequence was a
        // deadlock in the authority gate: a changed AGENTS.md holds dispatch
        // and prints "accept it with 'atropos auth accept AGENTS.md'", and that
        // command did nothing at all. The only remedy the engine offered was
        // one it did not implement.
        //
        // Reads and writes both go through the same router the interactive
        // session uses, so a one-shot cannot do anything a typed command
        // could not, and there is no second dispatch path to keep in step.
        val oneShot = args.filterNot { it == "--web" }
        if (oneShot.isNotEmpty()) {
            // Boot noise (the authority error, the continuity notice) has
            // already printed and is not this command's failure. Counting it
            // would make `atropos auth accept AGENTS.md` exit non-zero on the
            // very run that fixed the thing it was complaining about.
            ui.resetErrorCount()
            val line = oneShot.joinToString(" ").let { if (it.startsWith("/")) it else "/$it" }
            router.handleInput(line)
            exitProcess(if (ui.errorCount > 0) 1 else 0)
        }

        if (capabilities.isInteractiveTerminal) {
            atropos.cli.InteractiveCliRunner.runInteractive(capabilities, config, ui, tracker, router)
        } else {
            atropos.cli.InteractiveCliRunner.runHeadless(config, ui, router)
        }
    } catch (failure: Exception) {
        ui.renderError(
            "startup failed (${failure.javaClass.simpleName}): " +
                (failure.message ?: "unknown failure")
        )
    } finally {
        ui.cleanup()
    }
}

