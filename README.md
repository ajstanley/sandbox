# Islandora Sandbox <!-- omit in toc -->

[![LICENSE](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](./LICENSE)
![CI](https://github.com/Islandora-Devops/sandbox/workflows/CI/badge.svg?branch=main)

- [Introduction](#introduction)
- [Requirements](#requirements)
- [Running](#running)
- [Development](#development)
- [IDE](#ide)
- [Packaging](#packaging)
  - [Docker Desktop](#docker-desktop)
  - [Digital Ocean](#digital-ocean)
  - [UTM / QEMU](#utm--qemu)
  - [VirtualBox](#virtualbox)
- [Deployment](#deployment)

## Introduction

This repository is responsible for the packaging and uploading of releases of
the [Islandora Sandbox]. As well as for its deployment into [Digital Ocean].

It is **not** meant as a starting point for new users or those unfamiliar with
Docker, or basic server adminstration.

If you are looking to use islandora please read the [official documentation] and
use either [isle-dc] to deploy via Docker or the [islandora-playbook] to deploy
via Ansible.

## Requirements

To run the sandbox [locally](#running) only the following are required:

- [Docker 19.03+](https://docs.docker.com/get-docker/)
- [OpenJDK or Oracle JDK 8+](https://www.java.com/en/download/)

Additionally, to [package](#packaging) the sandbox for distribution:

- [Packer 1.8+](https://learn.hashicorp.com/tutorials/packer/get-started-install-cli)
- [QEMU 7.0+](https://www.qemu.org/download/)
- [Virtualbox 6.1+](https://www.virtualbox.org/wiki/Downloads)

Additionally, to [deploy](#deploying) the packaged images to Digital Ocean:

- [Terraform 1.0+](https://www.terraform.io/downloads)

## Running

To run locally use the command:

```bash
./gradlew up
```

This will bring up the environment with the appropriate folders bind mounted so
that changes made to the `sandbox-drupal` image are persisted, into this
repository. You will then be able to visit the site at
<https://sandbox.islandora.dev>, note that it may take **a few minutes** to start as
it is installing the site and importing data.

## Development

Use the packaged editor <https://ide.islandora.dev> to interact with the files
in the [drupal](./drupal) folder.

The basic workflow for altering the sandbox is as follows

1. Bring [up](#running) the site
2. To add/remove/update dependencies; use the terminal in the [ide](#ide) to
    1. Invoke Composer to add/remove/update dependencies: `composer update`
    2. Enable any new dependencies: `drush pm:enable ...`
    3. Update the database with Drush: `drush updatedb`
3. Make changes through web interface <https://sandbox.islandora.dev>
4. To export the changes to the site, use the terminal in the [ide](#ide) to
    1. Export configuration with Drush: `drush config:export`
    2. Export content with Drush `drush content-sync:export --entity-types=file,taxonomy_term,node,media,shortcut`
5. Use `git` to commit any changes made to composer, configuration, and content.

## IDE

The IDE can be accessed at <https://ide.islandora.dev>.

The IDE grants root access into and should **not** be used in production or
exposed to the internet, it is only intended to aid in local development.


## Packaging

Some additional system configuration is required to perform packaging.

To fetch manifests from dockerhub it is required that you login (due to rate
limiting), add the following to `~/.gradle/gradle.properties`:

```config
dockerhub.username=USERNAME
dockerhub.password=PASSWORD
```

### Docker Desktop

The recommend way to use the `sandbox` is with [Docker Desktop], see the
[guide](./desktop/README.md) for more info.

This guide is packaged with the required assets produced automatically via
Github Actions when a release is made in the repository. 

See `Releases` for the latest version.

### Digital Ocean

To build for [Digital Ocean] some additional configuration is needed to
authenticate, add the following to `~/.gradle/gradle.properties`:

```config
digitalocean.token=dop_v1_XXXXXXXXXXX
```

```bash
./gradlew :packer:buildDigitalOcean
```

### UTM / QEMU

While it is possible to build this package on multiple x86 systems it is not
recommended as it takes many hours. Instead users should build on an `arm64`
based Macintosh.

```bash
./gradlew :packer:buildQemu
```

### VirtualBox

To build the `ova` for use with Virtualbox use the following command:

```bash
./gradlew :packer:buildVirtualBox
```

## Deployment

This repository makes use of Github Actions, though not all artifacts are
produced by a release. Only the following are:

- [Docker Desktop](#docker-desktop)
- [Digital Ocean](#digital-ocean)

The other virtual machines can only be produced locally.

[Digital Ocean]: https://www.digitalocean.com/
[Islandora Sandbox]: https://sandbox.islandora.ca/
[islandora-playbook]: https://github.com/Islandora-Devops/islandora-playbook
[official documentation]: https://islandora.github.io/documentation/
[Virtualbox]: https://www.virtualbox.org/