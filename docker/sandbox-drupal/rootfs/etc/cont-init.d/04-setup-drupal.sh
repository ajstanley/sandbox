#!/usr/bin/with-contenv bash
set -e

source /etc/islandora/utilities.sh

function setup() {
    local site="${1}"
    shift
    local drupal_root=/var/www/drupal/web
    local site_url=$(drupal_site_env "${site}" "SITE_URL")
    local subdir=$(drupal_site_env "${site}" "SUBDIR")
    local site_directory="${drupal_root}/sites/${subdir}"
    local public_files_directory="${site_directory}/files"
    local private_files_directory="/var/www/drupal/private"
    local twig_cache_directory="${private_files_directory}/php"
    local default_settings="${drupal_root}/sites/default/default.settings.php"

    # Ensure the files directories are writable by nginx, as when it is a new volume it is owned by root.
    mkdir -p "${site_directory}" "${public_files_directory}" "${private_files_directory}" "${twig_cache_directory}"
    chown nginx:nginx "${site_directory}" "${public_files_directory}" "${private_files_directory}" "${twig_cache_directory}"
    chmod ug+rw "${site_directory}" "${public_files_directory}" "${private_files_directory}" "${twig_cache_directory}"

    # Create settings.php if it does not exists, required to install site.
    if [[ ! -f "${site_directory}/settings.php" ]]; then
        s6-setuidgid nginx cp "${default_settings}" "${site_directory}/settings.php"
    fi
}

function main() {
    # Make sure the default drush cache directory exists and is writeable.
    mkdir -p /tmp/drush-/cache
    chmod a+rwx /tmp/drush-/cache
    setup default
}
main
