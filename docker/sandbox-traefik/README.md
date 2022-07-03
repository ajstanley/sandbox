# Islandora Sandbox Traefik <!-- omit in toc -->

To circumvent the need for bind mounts we package our own traefik that can be
configured with environment variables alone.

This allows for the deployment of the sandbox with only a single
`docker-compose.yml` file.