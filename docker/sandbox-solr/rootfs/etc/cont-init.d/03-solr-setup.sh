#!/usr/bin/with-contenv bash
set -e

# Ensure that cores can be created on bind volume.
chown solr:solr /data
