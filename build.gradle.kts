import plugins.execCaptureOutput

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

val revision by extra {
    // Requires at least one tag to exist on the repository.
    execCaptureOutput(
        listOf("git", "describe", "--tags", "--long", "--always", "--dirty", "--broken"),
        "Failed to determine current revision of the repository."
    )
}

// Do not show output unless requested.
allprojects {
    tasks.configureEach {
        logging.captureStandardOutput(LogLevel.INFO)
        logging.captureStandardError(LogLevel.INFO)
    }
}

// Reasonable defaults.
subprojects {
    // Move build directory to be relative to the root.
    // Make all build directories relative to the root, only supports projects up to a depth of one for now.
    buildDir = rootProject.buildDir.resolve(projectDir.relativeTo(rootDir))
}
