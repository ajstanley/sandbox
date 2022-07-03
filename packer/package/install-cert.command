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

echo "Install the local CA in the system trust store."
"${MKCERT}" -install

@todo need to handle the root certificates here as well.

echo "Generate certificate."
mkdir -p "${OUT}"
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

echo "Copy the certificate to the the Virtual Machine."
scp -r -i "${PROGDIR}/vm_rsa" \
  -o "UserKnownHostsFile=/dev/null" \
  -o "StrictHostKeyChecking=no" \
  -P2222 \
  "${OUT}" core@islandora.dev:/opt/sandbox/build

echo "Restart Services."
ssh -i "${PROGDIR}/vm_rsa" \
    -o "UserKnownHostsFile=/dev/null" \
    -o "StrictHostKeyChecking=no" \
    -p2222 \
    core@islandora.dev 'sudo systemctl restart sandbox'
