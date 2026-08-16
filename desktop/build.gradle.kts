plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.runtime)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "atropos.desktop.DesktopApplicationKt"
    }
}
