val smokeTest = tasks.register<Exec>("smokeTest") {
    commandLine("echo", "test")
}
