plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    // The platform contract this surface renders — PlatformWire,
    // PlatformDescriptor, PlatformHealth — lives in the engine. Declared as a
    // coordinate rather than project(":"), because this is a separate build:
    // settings.gradle.kts includeBuild("..") substitutes the engine's root
    // project for it.
    implementation("atropos:ATROPOS:2.0.0")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.runtime)
    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "atropos.desktop.DesktopApplicationKt"
    }
}
