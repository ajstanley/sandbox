rootProject.name = "isle-sandbox"

include("certs", "compose", "docker", "packer", "terraform")

// Include any folder that has a Dockerfile as a subproject.
rootProject.projectDir.resolve("compose")
    .walk()
    .maxDepth(1) // Only immediate directories.
    .filter { it.isDirectory && it.resolve("docker-compose.yml").exists() }
    .forEach {
        include(":compose:${it.name}")
    }

// Include any folder that has a Dockerfile as a subproject.
rootProject.projectDir.resolve("docker")
    .walk()
    .maxDepth(1) // Only immediate directories.
    .filter { it.isDirectory && it.resolve("Dockerfile").exists() } // Must have a Dockerfile.
    .forEach { docker ->
        include(":docker:${docker.name}")
    }