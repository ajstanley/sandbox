#!/usr/bin/with-contenv bash
set -e

source /etc/islandora/utilities.sh

readonly SITE="default"
readonly QUEUES=(
  islandora-connector-fits
  islandora-connector-homarus
  islandora-connector-houdini
  islandora-connector-ocr
  islandora-indexing-fcrepo-content
  islandora-indexing-fcrepo-delete
  islandora-indexing-fcrepo-file-external
  islandora-indexing-fcrepo-media
  islandora-indexing-triplestore-delete
  islandora-indexing-triplestore-index
)

function drush {
  /usr/local/bin/drush --root=/var/www/drupal --uri="${DRUPAL_DRUSH_URI}" "$@"
}

function jolokia {
  local type="${1}"
  local queue="${2}"
  local action="${3}"
  local url="http://activemq:8161/api/jolokia/${type}/org.apache.activemq:type=Broker,brokerName=localhost,destinationType=Queue,destinationName=${queue}"
  if [ "$action" != "" ]; then
    url="${url}/$action"
  fi
  curl -u "admin:password" "${url}"
}

function pause_queues {
  for queue in "${QUEUES[@]}"; do
    jolokia "exec" "${queue}" "pause"
  done
}

function resume_queues {
  for queue in "${QUEUES[@]}"; do
    jolokia "exec" "${queue}" "resume"
  done
}

function purge_queues {
  for queue in "${QUEUES[@]}"; do
    jolokia "exec" "${queue}" "purge"
  done
}

function wait_for_dequeue {
  local queue_size=-1
  local continue_waiting=1
  while [ "${continue_waiting}" -ne 0 ]; do
    continue_waiting=0
    for queue in "${QUEUES[@]}"; do
      queue_size=$(jolokia "read" "${queue}" | jq .value.QueueSize)
      if [ "${queue_size}" -ne 0 ]; then
        continue_waiting=1
      fi
    done
    sleep 3
  done
}

function mysql_count_query {
    cat <<- EOF
SELECT COUNT(DISTINCT table_name)
FROM information_schema.columns
WHERE table_schema = '${DRUPAL_DEFAULT_DB_NAME}';
EOF
}

# Check the number of tables to determine if it has already been installed.
function installed {
    local count=$(execute-sql-file.sh <(mysql_count_query) -- -N 2>/dev/null)
    [[ $count -ne 0 ]]
}

function import {
  # Make sure the uuid matches what is stored in content-sync, clear caches.
  # Set the created/modified date to 1970 to allow it to be updated.
  drush sql:query "UPDATE users SET uuid='bd530a2b-ec6c-4e98-8b66-2621c688440b' WHERE uid=0"
  drush sql:query "UPDATE users SET uuid='2b939a79-0f98-444d-8de6-435d40eefbd0' WHERE uid=1"
  drush sql:query 'update users_field_data set created=1, changed=1 where uid=0'
  drush sql:query 'update users_field_data set created=1, changed=1  where uid=1'

  # Due to: https://www.drupal.org/project/content_sync/issues/3134102
  # Rebuild content-sync snapshot.
  drush sql:query "TRUNCATE cs_db_snapshot"
  drush sql:query "TRUNCATE cs_logs"
  drush cr
  drush php:eval "\Drupal::service('content_sync.snaphoshot')->snapshot(); drush_backend_batch_process();"

  # Pause queue consumption during import.
  pause_queues

  # Users must exists before all else.
  drush content-sync:import -y --entity-types=user

  # Home page needs to be node/1 as aliases do not get synced with configuration.
  drush content-sync:import -y --uuids=5d7e7461-5ff7-4207-aca3-e02d13535c18
  drush content-sync:import -y --entity-types=file,taxonomy_term,node,media,shortcut
  drush pathauto:aliases-generate all all

  # Files already exist clear the brokers to prevent generating derivatives again.
  purge_queues

  # Resume consumption of the queues.
  resume_queues

  # Index nodes and media and taxonomy terms.
  drush php:script /usr/local/share/sandbox/index.php

  # Add check to wait for queue's to empty
  wait_for_dequeue &

  # Add check to wait for solr index to complete.
  drush search-api:index &

  wait
}

function install {
  create_database "${SITE}" &
  create_blazegraph_namespace_with_default_properties "${SITE}" &
  wait
  # Install from configuration.
  install_site "${SITE}"
}

function main() {
    if installed; then
      echo "Already Installed"
    else
      install
      import
    fi
}
main