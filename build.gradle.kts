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
// The engine builds from mavenCentral alone. See settings.gradle.kts.
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
    implementation("org.json:json:20231013")
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

// Simple Kotlin compile options
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Build stamp task - simplified for Gradle Kotlin DSL compatibility
tasks.register("buildStamp") {
    doLast {
        val stampFile = layout.buildDirectory.file("generated/atropos-build.properties")
        val declaredVersion = project.version.toString()
        val head = "unknown"
        try {
            val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (output.isNotBlank()) {
                head = output
            }
        } catch (e: Exception) {
            // ignore
        }
        stampFile.get().asFile().parentFile.mkdirs()
        stampFile.get().asFile().writeText("version=$declaredVersion\ncommit=$head\n")
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
    dependsOn(tasks.named("buildStamp"))
    val stampFile = layout.buildDirectory.file("generated/atropos-build.properties")
    val declaredVersion = project.version.toString()
    val head = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.map { it.trim() }.orElse("unknown")

    outputs.file(stampFile)
    doLast {
        stampFile.get().asFile().parentFile.mkdirs()
        stampFile.get().asFile().writeText("version=$declaredVersion\ncommit=$head\n")
    }
}

// Packaging remains an explicit operator action, but the canonical installer
// owner is reachable from the root task graph instead of being a free script.
tasks.register("packageInstallers") {
    group = "distribution"
    description = "Package the already-built ATROPOS artifact for supported hosts."
    dependsOn(tasks.named("jar"))
    doLast {
        // Packaging script will be called
    }
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

val specGraphIndex by tasks.registering {
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
        exclude("**/__pycache__/**")
    }
    from(specGraphIndex) {
        into("specgraph")
    }
}

application {
    mainClass.set("atropos.MainKt")
}

// Kotlin compatibility scan
tasks.register("kotlinCompatScan") {
    group = "verification"
    description = "Scan Kotlin sources and dependencies for non-portable or unclassified APIs."
    doLast {
        val script = file("scripts/kotlin-compat-scan.sh")
        val process = ProcessBuilder("bash", script.absolutePath).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleException("Kotlin compatibility scan failed")
        }
    }
}

// Portable surface plan validation
tasks.register("portableSurfacePlan") {
    group = "verification"
    description = "Verify the canonical Docker/native/desktop/Android/Web migration plan is present."
    doLast {
        val planFile = file("docs/architecture/DOCKER_NATIVE_DESKTOP_ANDROID_WEB_PLAN.md")
        require(planFile.isFile) { "portable surface plan is missing: $planFile" }
        val plan = planFile.readText()
        listOf(
            "src/main/kotlin/atropos/core",
            "AtroposRepoRootLocator",
            "Packaging and installation proof",
            "must not create a second DAG"
        ).forEach { marker ->
            require(plan.contains(marker)) { "portable surface plan is missing required ownership marker: $marker" }
        }
    }
}

// Phase 0 toolchain contract test
tasks.register("phase0ToolchainContractTest") {
    group = "verification"
    description = "Check Phase 0 runtime, toolchain, compile-probe, and Git-state contracts without building."
    doLast {
        val script = file("scripts/phase0-toolchain-contract-test.sh")
        val process = ProcessBuilder("bash", script.absolutePath).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleException("Phase 0 toolchain contract test failed")
        }
    }
}

// Secret scan
tasks.register("secretScan") {
    group = "verification"
    description = "Run the secret-security and vault isolation proofs."
    doLast {
        val script = file("scripts/secret-security-proof.sh")
        val process = ProcessBuilder("bash", script.absolutePath).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleException("Secret scan failed")
        }
    }
}

// Smoke test
tasks.register("smokeTest") {
    group = "verification"
    description = "Build the jar and prove it starts and answers on stdin."
    dependsOn(tasks.named("jar"))
    doLast {
        val jar = layout.buildDirectory.file("libs/ATROPOS.jar").get().asFile
        require(jar.isFile) { "smoke test cannot run: ${jar.name} was not produced" }
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(javaBin, "-jar", jar.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.outputStream.write("/help\n".toByteArray())
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        require(output.contains("ATROPOS")) {
            "smoke test failed: jar produced no ATROPOS banner. Output was:\n$output"
        }
    }
}

tasks.named("check") {
    dependsOn("kotlinCompatScan", "portableSurfacePlan", "phase0ToolchainContractTest", "secretScan")
}