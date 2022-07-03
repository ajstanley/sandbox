package plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import tasks.DockerBuild
import tasks.FetchManifests
import java.io.ByteArrayInputStream
import tasks.ExportImages

class ExportImagesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        // Due to API limits for anonymous users we must login before fetching remote manifests.
        val login by tasks.registering(Exec::class) {
            val username = properties.getOrDefault("dockerhub.username", "") as String
            val password = properties.getOrDefault("dockerhub.password", "") as String
            standardInput = ByteArrayInputStream(password.toByteArray())
            commandLine =
                listOf(
                    "docker", "login", "--username", username, "--password-stdin"
                )
        }

        val fetchManifests by tasks.registering(FetchManifests::class) {
            dependsOn(login)
        }

        tasks.register<ExportImages>("export") {
            src.set(fetchManifests.flatMap { it.dest })
        }

    }

}