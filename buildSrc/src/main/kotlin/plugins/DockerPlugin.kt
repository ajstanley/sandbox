package plugins

import IsleDocker
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import tasks.DockerBuild
import tasks.ExportImages
import tasks.FetchManifests

class DockerPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        apply<IsleDocker>()

        tasks.register("build") {
            subprojects {
                dependsOn(tasks.withType<DockerBuild>())
            }
        }

        val envFileContents = rootProject.file(".env").readText()
        val repository = """ISLE_BUILDKIT_REPOSITORY=(.*)""".toRegex().find(envFileContents)!!.groups[1]!!.value
        val tag = """ISLE_BUILDKIT_TAG=(.*)""".toRegex().find(envFileContents)!!.groups[1]!!.value

        subprojects {
            // Use the same images as referenced by the compose projects.
            val build = tasks.withType<DockerBuild> {
                options.run {
                    buildArgs.set(
                        mapOf(
                            "repository" to repository,
                            "tag" to tag
                        )
                    )
                }
            }
            val fetchManifests by tasks.registering(FetchManifests::class) {
                // Do not cache manifests as local repository is ephemeral.
                outputs.cacheIf { false }
                outputs.upToDateWhen { false }
                // Update manifests if any build input changes.
                inputs.dir(this@subprojects.projectDir)
                // Force local repository to explicitly use port 80 as the local repository is insecure.
                this.images.set(build.flatMap {
                    it.options.tags.get()
                })
                // Build before fetching the new manifests.
                dependsOn(build)
            }
            tasks.register<ExportImages>("export") {
                src.set(fetchManifests.flatMap { it.dest })
            }
        }
    }

}