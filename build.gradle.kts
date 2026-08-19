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
version = "2.0.0"

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

// The canonical atomizer travels inside the jar.
//
// Without this, a fresh install has no SPECGRAPH_ROOT, SpecGraphAtomizer
// soft-fails to the weaker internal extractor, and a factory run quietly
// produces a fraction of the atoms the document contains. Asking an operator
// to `pkg install python && pip install -e .` on a phone before the tool works
// is not an install story -- the atomizer is part of the product.
//
// Source, not a wheel: the atom path imports nothing outside the standard
// library (pypdf and fpdf2 are lazy imports inside the PDF renderer, which
// atomization never reaches), so there is no `pip`, no network and no native
// wheel to carry. A python3 interpreter is still required and its absence is
// still reported honestly.
val specGraphSource = layout.projectDirectory.dir("apps/specgraph-foundry/src")

// Python cannot import from inside a jar, so BundledSpecGraph unpacks the tree
// on first use and needs to know what is in it. Enumerating a jar directory
// through a classloader is not reliable, so the manifest is written here.
val specGraphIndex by tasks.registering {
    // Both sides captured as plain files at configuration time. Reaching for
    // `specGraphSource` inside doLast captures the build script itself, which
    // the configuration cache cannot serialise -- the task then fails with a
    // null receiver rather than anything that names the real problem.
    val sourceDirectory = specGraphSource.asFile
    val indexFile = layout.buildDirectory.file("specgraph/INDEX").get().asFile

    inputs.dir(sourceDirectory).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(indexFile)

    doLast {
        val root = sourceDirectory.toPath()
        val files = sourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "py" }
            .filterNot { it.path.contains("__pycache__") }
            .map { root.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .sorted()
            .toList()
        require(files.isNotEmpty()) { "no SpecGraph sources found under $root" }
        indexFile.parentFile.mkdirs()
        indexFile.writeText(files.joinToString("\n", postfix = "\n"))
    }
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
    from(specGraphSource) {
        into("specgraph")
        include("**/*.py")
        // Compiled bytecode is machine- and version-specific and would make the
        // jar non-reproducible for no benefit.
        exclude("**/__pycache__/**")
    }
    from(specGraphIndex) {
        into("specgraph")
    }
}

// The build stamp the running jar can report.
//
// `atropos --version` had nothing to read. An operator on a phone cannot
// rebuild -- every install comes from a release asset -- so "is the binary I
// am running the one I just pulled?" was answerable only by hashing the jar
// against a URL, and when it was not, the symptom was a fix that appeared not
// to work. The version and the commit are written at build time and read back
// at runtime.
val buildStamp by tasks.registering {
    val stampFile = layout.buildDirectory.file("generated/atropos-build.properties").get().asFile
    val declaredVersion = project.version.toString()
    val head = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.map { it.trim() }.orElse("unknown")

    outputs.file(stampFile)
    doLast {
        stampFile.parentFile.mkdirs()
        stampFile.writeText(
            "version=$declaredVersion\ncommit=${head.getOrElse("unknown")}\n"
        )
    }
}

sourceSets.named("main") {
    output.dir(mapOf("builtBy" to buildStamp), layout.buildDirectory.dir("generated"))
}

// Stale test scratch is swept before the suite, not after.
//
// The suite creates temporary directories in a hundred and seventy-seven
// places and deletes almost none of them; on the operator's phone that had
// grown to roughly sixty abandoned copies of a 9 MB jar. Sweeping before the
// run rather than after means a crashed or killed run still gets cleaned up on
// the next one, which is exactly the case that produced the pile.
//
// Bounded to this project's own prefix and to directories older than a day, so
// it cannot touch another run's live scratch.
tasks.withType<Test>().configureEach {
    doFirst {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val temp = File(System.getProperty("java.io.tmpdir"))
        temp.listFiles { file -> file.isDirectory && file.name.startsWith("atropos-") }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }
}
