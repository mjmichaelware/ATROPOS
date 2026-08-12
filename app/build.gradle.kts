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
        // A package name no previously installed build occupies.
        //
        // Earlier builds were installed as com.atropos.android.app,
        // com.atropos.fixed.v2 and com.atropos.v3.unique, all signed with a
        // debug key. This build is signed with the operator's release
        // keystore, and Android refuses to replace an installed app whose
        // signing key differs — reported through the GUI installer as the same
        // "app wasn't installed" as every other failure. Clearing that
        // normally means uninstalling the old copies, which needs either adb
        // or the Settings app; adb needs Wireless debugging, which needs Wi-Fi.
        //
        // A fresh id has nothing to compare against, so it installs cleanly
        // with no uninstall step. This is now the permanent identity: keep it
        // and the release keystore stable so future builds upgrade in place.
        applicationId = "com.atropos.engine"
        minSdk = 28
        targetSdk = 34
        versionCode = 1001
        versionName = "10.0.1"
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
