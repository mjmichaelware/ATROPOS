plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("atropos.server.MainKt")
}

dependencies {
    implementation(project(":"))
    implementation(project(":core"))
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    testImplementation(kotlin("test-junit"))
}
