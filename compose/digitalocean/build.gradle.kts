import plugins.DockerComposePlugin.DockerCompose

tasks.named<DockerCompose>("wait") {
    doLast {
        logger.quiet(
            """
            ActiveMQ: https://activemq.islandora.ca/
            Blazegraph: https://blazegraph.islandora.ca/bigdata/
            Drupal: https://sandbox.islandora.ca/
            Fedora: https://fcrepo.islandora.ca/fcrepo/rest/
            Matomo: https://sandbox.islandora.ca/matomo/index.php
            Solr: https://solr.islandora.ca/solr/#/
            Traefik: https://traefik.islandora.ca/dashboard/#/
            """.trimIndent()
        )
    }
}