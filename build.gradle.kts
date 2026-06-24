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
}

application {
    mainClass.set("atropos.MainKt")
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
