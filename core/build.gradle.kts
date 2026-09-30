plugins {
    kotlin("multiplatform")
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

// `./gradlew test` must reach this module's tests.
//
// A multiplatform project names its test tasks per target — `jvmTest`, and
// `allTests` over all of them — and never `test`. `./gradlew test` runs every
// task *called* `test` in every project, so this module was silently skipped:
// the build went green having executed none of its tests, which is worse than
// a module with no tests at all, because the report looks the same as one that
// passed. This alias makes the name every other module answers to reach the
// tests this one actually has.
tasks.register("test") {
    group = "verification"
    description = "Runs this module's tests under the name the rest of the build uses."
    dependsOn(tasks.named("allTests"))
}
