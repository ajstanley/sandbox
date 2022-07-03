#!/usr/bin/env bash

set -e

readonly PROGDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PLATFORM="$(uname | tr "[:upper:]" "[:lower:]")"
if [[ $(uname -m) == "x86_64" ]]; then
  readonly ARCH="amd64"
else
  readonly ARCH="arm64"
fi
readonly MKCERT="${PROGDIR}/bin/mkcert-${PLATFORM}-${ARCH}"
readonly OUT="${PROGDIR}/certs"

mkdir -p "${OUT}"

echo "Install the local CA in the system trust store."
(
  unset JAVA_HOME
  "${MKCERT}" -install
)

cp "$(${MKCERT} -CAROOT)/rootCA.pem" "${OUT}"

echo "Generate certificate."
(
  cd "${PROGDIR}"
  "${MKCERT}" \
    -cert-file "${OUT}/cert.pem" \
    -key-file "${OUT}/privkey.pem" \
    "*.islandora.dev" \
    "islandora.dev" \
    "localhost" \
    "127.0.0.1" \
    "::1"
)

echo "Creating docker-compose.yml"
(
  cd "${PROGDIR}/assets"
  export CERT_PUBLIC_KEY="$(cat ${OUT}/cert.pem)"
  export CERT_PRIVATE_KEY="$(cat ${OUT}/privkey.pem)"
  export CERT_AUTHORITY="$(cat ${OUT}/rootCA.pem)"
  export TAG="$(cat ${PROGDIR}/assets/TAG)"
  # Sed is required for the bug https://github.com/docker/compose/issues/9306
  docker compose -f docker-compose.desktop.yml config | sed 's/"80"/80/g' |  sed 's/"443"/443/g' | sed 's/^name: sandbox$/version: "3.8"/g' > "${PROGDIR}/docker-compose.yml"
)

echo "Finished"
