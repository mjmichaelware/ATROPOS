plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.atropos.android.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atropos.android.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1000
        versionName = "10.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
