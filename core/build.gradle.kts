plugins {
    kotlin("multiplatform") version "1.9.24"
}

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
