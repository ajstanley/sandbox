import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import plugins.DockerComposePlugin.Config
import plugins.DockerComposePlugin.DockerCompose
import plugins.ExportImagesPlugin
import tasks.FetchManifests

apply<ExportImagesPlugin>()

val platform = getCurrentOperatingSystem()!!

evaluationDependsOn(":docker")

tasks.withType<DockerCompose> {
    environment.put("REPOSITORY", "registry.islandora.dev:5000")
}

// Must build the images that wil>l be exported into the VM for use by the VM.
tasks.named<DockerCompose>("build") {
    dependsOn(":docker:build")
}

tasks.named<DockerCompose>("wait") {
    doLast {
        logger.quiet("""
            ActiveMQ: https://activemq.islandora.dev:8443/
            Blazegraph: https://blazegraph.islandora.dev:8443/bigdata/
            Drupal: https://sandbox.islandora.dev:8443/
            Fedora: https://fcrepo.islandora.dev:8443/fcrepo/rest/
            Matomo: https://sandbox.islandora.dev:8443/matomo/index.php
            Solr: https://solr.islandora.dev:8443/solr/#/
            Traefik: https://traefik.islandora.dev:8443/dashboard/#/
        """.trimIndent()
        )
    }
}

tasks.named<FetchManifests>("fetchManifests") {
    val images = rootProject.project(":docker").subprojects.map { it.name }
    this.images.set(tasks.named<DockerCompose>("config").map { task ->
        Config.fromFile(task.outputFile.get())
            .services
            .filter { (_, service) ->
                // Export those images not build locally.
                !images.any { service.image?.contains(it) ?: false }
            }.map { (_, service) ->
                service.image
            }
    })
}
