import plugins.DownloadsPlugin.DownloadsExtension.Companion.downloads
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

tasks.register<Zip>("package") {
    val current = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val date = current.format(formatter)
    // Github actions will override this with the git branch to mark release versions.
    val tag = (properties["docker.tags"] as String).split(",").first()
    archiveFileName.set("islandora-sandbox-${tag}-${date}.zip")
    destinationDirectory.set(layout.buildDirectory)
    // Include all platform version of mkcert tool.
    from(rootProject.project(":certs").downloads.all()) {
        exclude("*.sha256")
        into("bin")
    }
    // Include all scripts & assets intended to be packed with the Docker Desktop release.
    from(layout.projectDirectory.dir("package"))
    // Package the docker-compose.yml & .env file for portainer, rename it to be distinct so end-users do not get
    // confused with the generated docker-compose.yml file.
    from(rootProject.project(":compose:portainer").file("docker-compose.yml")) {
        rename {
            "docker-compose.desktop.yml"
        }
        into("assets")
    }
    from(rootProject.project(":compose").file(".env")) {
        into("assets")
    }
    // Use the generated TAG file to determine to specify the release tag of the images built by this repository.
    from(layout.buildDirectory.file("TAG")) {
        into("assets")
    }
    into("islandora-sandbox")
    doFirst {
        layout.buildDirectory.file("TAG").get().asFile.writeText(tag)
    }
}