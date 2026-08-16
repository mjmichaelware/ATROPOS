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
// neither owns engine policy or durable state. Both resolve from mavenCentral,
// which is what lets them stay in this build.
//
// :desktop is deliberately absent. Compose Desktop resolves androidx artifacts
// from Google's maven, so including it here made `./gradlew test` fail to
// resolve on a mavenCentral-only device — the same trap the Android client is
// kept out of this build to avoid. It is a standalone build:
//   ./gradlew -p desktop run
include(":core", ":server")
