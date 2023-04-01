# Islandora Sandbox <!-- omit in toc -->

[![LICENSE](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](./LICENSE)
[![Build](https://github.com/Islandora-Devops/sandbox/actions/workflows/push.yml/badge.svg)](https://github.com/Islandora-Devops/sandbox/actions/workflows/push.yml)

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Running](#running)
- [Github Actions](#github-actions)

## Introduction

This repository is responsible for the packaging and uploading of releases of
the [Islandora Sandbox]. As well as for its deployment into [Digital Ocean].

It is **not** meant as a starting point for new users or those unfamiliar with
Docker, or basic server administration.

If you are looking to use islandora please read the [official documentation] and
use either [isle-dc] or [isle-site-template] to deploy via Docker or the
[islandora-playbook] to deploy via Ansible.

## Requirements

- [Docker 20.10+](https://docs.docker.com/get-docker/)
- [GNU Make 4.3+](https://www.gnu.org/software/make/)
- [jq 1.6+](https://stedolan.github.io/jq/)
- [mkcert 1.4+](https://github.com/FiloSottile/mkcert)

> N.B The version of `make` that comes with OSX is to old, please update
> using `brew` etc.

## Running

To run locally use the command:

```bash
make up
```

This will bring up the environment based on [islandora-starter-site]. When
completed a message will print like so:

```bash
Waiting for installation...  Credentials:
  Username                       admin
  Password                       password

  Services Available:
  Drupal                         http://islandora.io
  ActiveMQ                       http://activemq.islandora.io
  Blazegraph                     http://blazegraph.islandora.io/bigdata/
  Fedora                         http://fcrepo.islandora.io/fcrepo/rest/
  Matomo                         http://islandora.io/matomo/index.php
  Cantaloupe                     http://islandora.io/cantaloupe
  Solr                           http://solr.islandora.io
  Traefik                        http://traefik.islandora.io
```

> N.B. It may take **a few minutes** to start as it is installing the site and
> importing data.

## Github Actions

This repository makes use of [Github Actions] to perform a number of tasks.

| Workflow                                   | Description                                                                           |
| :----------------------------------------- | :------------------------------------------------------------------------------------ |
| [push.yml](.github/workflows/push.yml)     | Builds and pushes `islandora/sandbox:main` image.                                     |
| [deploy.yml](.github/workflows/deploy.yml) | Builds and pushes `islandora/sandbox:${TAG}` image and deploys it to [Digital Ocean]. |

[Digital Ocean]: https://www.digitalocean.com/
[Islandora Sandbox]: https://sandbox.islandora.ca/
[islandora-playbook]: https://github.com/Islandora-Devops/islandora-playbook
[islandora-starter-site]: https://github.com/Islandora/islandora-starter-site
[isle-site-template]: https://github.com/Islandora-Devops/isle-site-template
[official documentation]: https://islandora.github.io/documentation/


@todo document github actions

Note that Create Fedora CoreOS snapshot does not wait and may take a long time 1h+.