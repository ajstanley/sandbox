# Islandora Sandbox <!-- omit in toc -->

[![LICENSE](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](./LICENSE)
[![Build](https://github.com/Islandora-Devops/sandbox/actions/workflows/push.yml/badge.svg)](https://github.com/Islandora-Devops/sandbox/actions/workflows/push.yml)

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Running](#running)
- [Updating](#updating)
- [Releases](#releases)
- [Github Actions](#github-actions)
  - [Deployment Environments](#deployment-environments)
  - [Environment Variables](#environment-variables)
  - [Domains](#domains)
- [Volumes](#volumes)

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

## Updating

## Releases

Minting a new releases will trigger two [actions](#github-actions):

1. Push a new deployment to [Digital Ocean] performed by [deploy.yml].
2. Package a zip file for local use and attach it to the release, performed by [package.yml].

When minting a new release, go to [releases] in the GitHub repository, and
perform the following steps:

1. Select `Draft new release`.
2. Enter a new tag by bumping the number of the previous release.
3. Select `Create a new tag: x.x.x on publish` targeting the `main` branch.
4. Select `Generate release notes`.
5. Add any additional notes you think relevant.
6. Make sure `Set as the latest release` is selected.
7. Click `Publish release`.

This will trigger the afore mentioned actions.

It will first deploy to: <https://test.sandbox.islandora.ca>

You can then visit and review the deployment before either canceling the
workflow at:

<https://github.com/Islandora-Devops/sandbox/actions/workflows/deploy.yml>

Or approving it which will then deploy to: <https://sandbox.islandora.ca>

After which point it will destroy the deployment to: <https://test.sandbox.islandora.ca>

## Github Actions

This repository makes use of [Github Actions] to perform a number of tasks.

| Workflow       | Trigger                  | Description                                                                                                                                                                                                              |
| :------------- | :----------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [deploy.yml]   | On new tag               | Builds and pushes `islandora/sandbox:${TAG}` image and deploys it to [Digital Ocean].                                                                                                                                    |
| [package.yml]  | On new release           | Creates a zip file containing a Docker Compose configuration file for an Islandora Sandbox, and attaches it to a GitHub release.                                                                                         |
| [push.yml]     | On push to `main` branch | Builds and pushes `islandora/sandbox:main` image.                                                                                                                                                                        |
| [snapshot.yml] | Manually                 | Creates a snapshot of a Fedora CoreOS image on DigitalOcean. This is used as the base image of deployments to [Digital Ocean]. Note that this **does not wait** for completion and may take a long time 1h+ to complete. |

The above workflows make use of the following [reusable workflows]:

| Workflow     | Description                                                                                                                                 |
| :----------- | :------------------------------------------------------------------------------------------------------------------------------------------ |
| [bake.yml]   | Builds and pushes a Docker image to a Docker registry.                                                                                      |
| [create.yml] | Creates a DigitalOcean Droplet and assigns it a reserved IP address. Destroying any preexisting droplet with the same name before creation. |

### Deployment Environments

We make use of two [deployment environments] for [GitHub Actions]. Which are
defined in the [environment settings] the GitHub repository.

- test
- sandbox

### Environment Variables

Each of the deployment environments have specific variables use to distinguish
them from one another.

| Deployment Environment | DOMAIN                    | RESERVED_IP_ADDRESS | VOLUME_NAME          |
| :--------------------- | :------------------------ | :------------------ | :------------------- |
| test                   | test.sandbox.islandora.ca | 174.138.112.33      | test-certificates    |
| sandbox                | sandbox.islandora.ca      | 159.203.49.92       | sandbox-certificates |

Aside from that a number of variables are shared between both environments.

| Variable      | Example Value                                                | Description                                                                            |
| :------------ | :----------------------------------------------------------- | :------------------------------------------------------------------------------------- |
| REGION        | `tor1`                                                       | The region to deploy [Digital Ocean] droplets into.                                    |
| SIZE          | `s-4vcpu-8gb-intel`                                          | The size of the droplet to create when deploying.                                      |
| SNAPSHOT_NAME | `fedora-coreos-37.20230205.3.0-digitalocean.x86_64.qcow2.gz` | The snapshot image to use when deploying, created by the [snapshot.yml] GitHub Action. |
| SSH_KEY_NAME  | `default`                                                    | The ssh key to deploy to the droplet on creation.                                      |

### Domains

The Domains are registered via [hover] though we use [Digital Ocean] nameservers
instead of those provided by [hover], as we needed supported for DNS challenges
to automatically generate wildcards certificates, and LetsEncrypt does not
support [hover].

The `DOMAIN` and `RESERVED_IP_ADDRESS` mentioned in the
[previous section](#environment-variables) needs to match the `A Records` in the
nameservers setup in [Digital Ocean].

## Volumes

Each volume referenced by the `VOLUME_NAME` environment variable refers to a
manually configured volume which stores the certificates generated by
LetsEncrypt. This is done to avoid hitting rate limit problems when doing
multiple deployments in a week, as the number of requests is allowed by
LetsEncrypt is very low.

[bake.yml]: .github/workflows/bake.yml
[create.yml]: .github/workflows/create.yml
[deploy.yml]: .github/workflows/deploy.yml
[deployment environments]: https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment
[Digital Ocean]: https://www.digitalocean.com/
[environment settings]: https://github.com/Islandora-Devops/sandbox/settings/environments
[Github Actions]: https://docs.github.com/en/actions/quickstart
[hover]: https://www.hover.com/
[Islandora Sandbox]: https://sandbox.islandora.ca/
[islandora-playbook]: https://github.com/Islandora-Devops/islandora-playbook
[islandora-starter-site]: https://github.com/Islandora/islandora-starter-site
[isle-site-template]: https://github.com/Islandora-Devops/isle-site-template
[official documentation]: https://islandora.github.io/documentation/
[package.yml]: .github/workflows/package.yml
[push.yml]: .github/workflows/push.yml
[releases]: https://github.com/Islandora-Devops/sandbox/releases
[reusable workflow]: https://docs.github.com/en/actions/using-workflows/reusing-workflows
[snapshot.yml]: .github/workflows/snapshot.yml

