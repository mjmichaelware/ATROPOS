plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    application
}

group = "atropos"
version = "2.0.0-rc.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit"))
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

tasks.named("check") {
    dependsOn(kotlinCompatScan, kotlinCompatScanEdge, portableSurfacePlan)
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
