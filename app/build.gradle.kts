// The ATROPOS Android client.
//
// This module is deliberately small: it is a client of the engine, not a copy
// of it. A previous change copied the entire JVM source and test trees into
// app/src/main/main/kotlin and app/src/main/test/kotlin — neither is a valid
// Android source set, so ~890 files were packaged nowhere, duplicated the
// canonical owners under src/, and added 92k lines to the repository.
plugins {
    id("com.android.application")
    // Canonical plugin id. The legacy "kotlin-android" alias resolves only
    // when the version is declared elsewhere, and it silently disagreed with
    // the root project's Kotlin version.
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atropos.android.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.atropos.android.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1000
        versionName = "10.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed in CI by the release workflow, not with the debug key.
            // Modern Android increasingly refuses debug-signed APKs through
            // the GUI installer, which is why installs failed even after the
            // package identity was made unique.
            signingConfig = null
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
