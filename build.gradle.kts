// Root project: the ATROPOS JVM engine, and nothing else.
//
// The Android client is a *separate Gradle build* under app/, not a subproject
// here. That separation is deliberate. A previous change replaced this file
// with an Android-only plugin block, which removed the JVM build, the fat jar,
// and the verification tasks wired into `check`. Restoring them as a
// multi-project build with the Android plugin declared here would trade one
// failure for another: the Android Gradle Plugin is resolved at configuration
// time for the whole build, so `./gradlew jar` would need to reach Google's
// maven before it could compile a single line of engine code. On an offline
// or restricted device — the aarch64 Termux target — that makes the engine
// unbuildable for a reason that has nothing to do with the engine.
//
// The engine builds from mavenCentral alone. See app/settings.gradle.kts.
// The Kotlin version is declared once, here. A subproject that restates it
// fails configuration outright — the plugin is already on the build classpath
// from this block, and Gradle refuses to re-resolve a version it cannot check
// against. `apply false` puts the multiplatform variant on the classpath for
// :core without applying it to the engine, which is a JVM project.
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.kotlin.multiplatform") version "1.9.24" apply false
    application
}

group = "atropos"
version = "2.0.0-rc.1"

// No `repositories` block here on purpose: settings.gradle.kts sets
// RepositoriesMode.FAIL_ON_PROJECT_REPOS, so declaring project-level
// repositories fails configuration. Dependency repositories are declared once,
// in settings.

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":core"))
    testImplementation(kotlin("test-junit"))
}

// Phase 0 baseline lock: pin the bytecode target, not the toolchain.
//
// The output is now identical Java 17 bytecode regardless of which JDK runs
// the build, which is the property the baseline actually needs. jvmToolchain(17)
// would be the stricter pin, but it *requires* JDK 17 specifically to be
// installed and fails the build wherever it is not — including an aarch64
// Termux device that ships 21, and this container, which has only 21. A
// baseline lock that cannot build on the target device is not a lock.
//
// Any JDK 17 or newer can build this; the artifact does not vary.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("atropos.MainKt")
}

val kotlinCompatScan = tasks.register<Exec>("kotlinCompatScan") {
    group = "verification"
    description = "Scan Kotlin sources and dependencies for non-portable or unclassified APIs."
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/kotlin-compat-scan.sh").asFile.absolutePath
    )
}

val kotlinCompatScanEdge = tasks.register<Exec>("kotlinCompatScanEdge") {
    group = "verification"
    description = "Exercise Kotlin compatibility scanner pass and refusal edges."
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/kotlin-compat-scan-test.sh").asFile.absolutePath
    )
}

val portableSurfacePlan = tasks.register("portableSurfacePlan") {
    group = "verification"
    description = "Verify the canonical Docker/desktop/Android/Web migration plan is present."
    val planFile = layout.projectDirectory.file("docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md")
    inputs.file(planFile)
    doLast {
        check(planFile.asFile.isFile) { "portable surface plan is missing: ${planFile.asFile}" }
        val plan = planFile.asFile.readText()
        listOf(
            "src/main/kotlin/atropos/core",
            "AtroposRepoRootLocator",
            "Packaging and installation proof",
            "must not create a second DAG"
        ).forEach { marker ->
            check(plan.contains(marker)) {
                "portable surface plan is missing required ownership marker: $marker"
            }
        }
    }
}

val phase0ToolchainContractTest = tasks.register<Exec>("phase0ToolchainContractTest") {
    group = "verification"
    description = "Check Phase 0 runtime, toolchain, compile-probe, and Git-state contracts without building."
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/phase0-toolchain-contract-test.sh").asFile.absolutePath
    )
}

val canonicalAcceptanceTest = tasks.register<Test>("canonicalAcceptanceTest") {
    group = "verification"
    description = "Run the canonical acceptance contract suite without broadening the test target."
    filter {
        includeTestsMatching("atropos.core.acceptance.CanonicalAcceptanceTests")
    }
}

// Packaging remains an explicit operator action, but the canonical installer
// owner is reachable from the root task graph instead of being a free script.
val packageInstallers = tasks.register<Exec>("packageInstallers") {
    group = "distribution"
    description = "Package the already-built ATROPOS artifact for supported hosts."
    dependsOn(tasks.named("jar"))
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/package-installers.sh").asFile.absolutePath
    )
}

// Phase 0 secret scan: the proof scripts existed but nothing ran them, so a
// leak could reach a release without any gate objecting.
val secretScan = tasks.register<Exec>("secretScan") {
    group = "verification"
    description = "Run the secret-security and vault isolation proofs."
    commandLine(
        "bash",
        layout.projectDirectory.file("scripts/secret-security-proof.sh").asFile.absolutePath
    )
}

// Phase 0 smoke proof: packaging succeeded without anyone proving the jar can
// actually start. Runs the built artifact headless and requires the banner.
val smokeTest = tasks.register("smokeTest") {
    group = "verification"
    description = "Build the jar and prove it starts and answers on stdin."
    dependsOn(tasks.named("jar"))
    // Resolved at configuration time. Touching `layout` inside doLast captures
    // the Project reference and breaks the configuration cache.
    val jarLocation = layout.buildDirectory.file("libs/ATROPOS.jar")
    doLast {
        val jar = jarLocation.get().asFile
        check(jar.isFile) { "smoke test cannot run: ${jar.name} was not produced" }
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(javaBin, "-jar", jar.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.outputStream.write("/help\n".toByteArray())
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        check(output.contains("ATROPOS")) {
            "smoke test failed: jar produced no ATROPOS banner. Output was:\n$output"
        }
    }
}

tasks.named("check") {
    dependsOn(kotlinCompatScan, kotlinCompatScanEdge, portableSurfacePlan, phase0ToolchainContractTest, secretScan, canonicalAcceptanceTest)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "atropos.MainKt"
    }
    archiveFileName.set("ATROPOS.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    }
}
