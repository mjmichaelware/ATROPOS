// Standalone Gradle build for the ATROPOS Compose Desktop surface.
//
// Separate from the engine build for the same reason app/ is. Compose Desktop
// 1.6.11 resolves androidx artifacts — annotation, collection, lifecycle —
// that are published to Google's maven and not to mavenCentral. Including
// :desktop in the engine's settings therefore made every engine command that
// resolves a runtime classpath (`./gradlew test`, `check`, `build`) fail
// outright on a machine that can reach only mavenCentral, which is the aarch64
// Termux target the engine is built on.
//
// Build from the repository root:
//   ./gradlew -p desktop run
//   ./gradlew -p desktop test
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.24"
        id("org.jetbrains.compose") version "1.6.11"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// desktop/ is itself the module, so it is the root project of this build.
rootProject.name = "atropos-desktop"

// The engine, as a composite. `atropos:ATROPOS` is substituted for the
// engine build's root project, so the surface compiles against the live
// tree rather than a published artifact.
includeBuild("..")
