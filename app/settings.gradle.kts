// Standalone Gradle build for the ATROPOS Android client.
//
// This is a separate build from the engine so that Android tooling is resolved
// only when an APK is actually being produced. Plugin versions are declared
// here rather than in a root `plugins { ... apply false }` block, which keeps
// app/build.gradle.kts free of version literals and means nothing resolves the
// Android Gradle Plugin unless this build runs.
//
// Build from the repository root:
//   ./gradlew -p app assembleRelease
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // AGP 8.2 requires Gradle 8.2+; the wrapper is pinned to 8.7.
        // Gradle 9 removed org.gradle.api.internal.HasConvention, which the
        // Kotlin Android plugin still relies on, so an unpinned wrapper that
        // resolves to Gradle 9 crashes this build during initialization.
        id("com.android.application") version "8.2.0"
        id("org.jetbrains.kotlin.android") version "1.9.24"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// app/ is itself the application module, so it is the root project of this
// build. No include(): a nested ":app" would resolve to app/app/.
rootProject.name = "atropos-android"
