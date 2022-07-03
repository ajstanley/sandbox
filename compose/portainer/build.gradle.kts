import plugins.DockerComposePlugin.DockerCompose

tasks.named<DockerCompose>("wait") {
    doLast {
        logger.quiet(
            """
            ActiveMQ: https://activemq.islandora.dev/
            Blazegraph: https://blazegraph.islandora.dev/bigdata/
            Drupal: https://sandbox.islandora.dev/
            Fedora: https://fcrepo.islandora.dev/fcrepo/rest/
            Matomo: https://sandbox.islandora.dev/matomo/index.php
            Solr: https://solr.islandora.dev/solr/#/
            Traefik: https://traefik.islandora.dev/dashboard/#/
            """.trimIndent()
        )
    }
}