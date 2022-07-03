# syntax=docker/dockerfile:1.2.1
ARG repository=islandora
ARG tag=latest
FROM --platform=$BUILDPLATFORM ${repository}/composer:${tag} AS composer

# Ignores changes in /var/www/drupal/config as it is not required for composer.
# Reduces the time to build and size of the image.
COPY rootfs/var/www/drupal/composer.json rootfs/var/www/drupal/composer.lock /var/www/drupal/
# Assets are referenced in composer file.
COPY rootfs/var/www/drupal/assets /var/www/drupal/assets

RUN --mount=type=cache,id=sandbox-drupal-composer,sharing=locked,target=/root/.composer/cache \
    composer install -d /var/www/drupal

FROM ${repository}/drupal:${tag}

# jq used by install script to parse the output of ActiveMQ checks.
RUN apk add jq

COPY rootfs /

COPY --chown=nginx:nginx --from=composer /var/www/drupal /var/www/drupal
