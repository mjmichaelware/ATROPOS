// The engine build. Deliberately does not include :app.
//
// The Android client is a standalone build under app/ with its own
// settings.gradle.kts. Including it here would make every engine command —
// `./gradlew jar`, `test`, `compileKotlin` — configure the Android module and
// therefore resolve the Android Gradle Plugin from Google's maven before doing
// any work. The engine must stay buildable from mavenCentral alone so it can
// be built on a restricted or offline device.
//
// Build the APK with:  ./gradlew -p app assembleRelease
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ATROPOS"

// Portable contracts and the optional Ktor transport are separate modules;
// neither owns engine policy or durable state.
include(":core", ":server", ":desktop")
