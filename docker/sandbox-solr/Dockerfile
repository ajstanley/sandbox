# syntax=docker/dockerfile:1.2.1
ARG repository=islandora
ARG tag=latest
FROM ${repository}/solr:${tag} AS solr

COPY rootfs /

RUN chown -R solr:solr /opt/solr
