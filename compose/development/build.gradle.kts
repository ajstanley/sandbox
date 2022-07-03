import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import plugins.DockerComposePlugin.DockerCompose

val platform = getCurrentOperatingSystem()!!

tasks.named<DockerCompose>("up") {
    // Profile is used for selecting the appropriate code-server service.
    when {
        platform.isLinux -> {
            options.addAll(listOf("--profile",  "Linux"))
        }
        platform.isMacOsX -> {
            options.addAll(listOf("--profile",  "Darwin"))
        }
    }
}

tasks.named<DockerCompose>("wait") {
    doLast {
        logger.quiet(
            """
            ActiveMQ: https://activemq.islandora.dev/
            Blazegraph: https://blazegraph.islandora.dev/bigdata/
            Drupal: https://sandbox.islandora.dev/
            Fedora: https://fcrepo.islandora.dev/fcrepo/rest/
            IDE: https://ide.islandora.dev/?folder=/var/www/drupal
            Matomo: https://sandbox.islandora.dev/matomo/index.php
            Solr: https://solr.islandora.dev/solr/#/
            Traefik: https://traefik.islandora.dev/dashboard/#/
            """.trimIndent()
        )
    }
}