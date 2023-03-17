###############################################################################
# Variables
###############################################################################
variable "ISLANDORA_REPOSITORY" {
  default = "islandora"
}

variable "ISLANDORA_TAG" {
  default = "latest"
}

variable "CACHE_FROM_REPOSITORY" {
  default = "islandora"
}

variable "CACHE_TO_REPOSITORY" {
  default = "islandora"
}

variable "REPOSITORY" {
  default = "islandora"
}

variable "TAG" {
  # "local" is to distinguish that from builds produced locally.
  default = "local"
}

variable "SOURCE_DATE_EPOCH" {
  default = "0"
}

###############################################################################
# Functions
###############################################################################
function hostArch {
  params = []
  result = equal("linux/amd64", BAKE_LOCAL_PLATFORM) ? "amd64" : "arm64" # Only two platforms supported.
}

function "tags" {
  params = [image, arch]
  result = ["${REPOSITORY}/${image}:${TAG}-${arch}"]
}

function "tags" {
  params = [image, suffix]
  result = equal("", suffix) ? ["${REPOSITORY}/${image}:${TAG}"] : ["${REPOSITORY}/${image}:${TAG}-${suffix}"]
}

function "cacheFrom" {
  params = [image, arch]
  result = ["type=registry,ref=${CACHE_FROM_REPOSITORY}/cache:${image}-main-${arch}", "type=registry,ref=${CACHE_FROM_REPOSITORY}/cache:${image}-${TAG}-${arch}"]
}

function "cacheTo" {
  params = [image, arch]
  result =  ["type=registry,oci-mediatypes=true,mode=max,compression=estargz,compression-level=5,ref=${CACHE_TO_REPOSITORY}/cache:${image}-${TAG}-${arch}"]
}

###############################################################################
# Groups
###############################################################################
group "default" {
  targets = [
    "sandbox"
  ]
}

group "amd64" {
  targets = [
    "sandbox-amd64",
  ]
}

group "arm64" {
  targets = [
    "sandbox-arm64",
  ]
}

# CI should build both and push to the remote cache.
group "ci" {
  targets = [
    "sandbox-amd64-ci",
    "sandbox-arm64-ci",
  ]
}

###############################################################################
# Common target properties.
###############################################################################
target "common" {
  args = {
    # Required for reproduciable builds.
    # Requires Buildkit 0.11+
    # See: https://reproducible-builds.org/docs/source-date-epoch/
    SOURCE_DATE_EPOCH = "${SOURCE_DATE_EPOCH}",
  }
}

target "amd64-common" {
  platforms = ["linux/amd64"]
}

target "arm64-common" {
  platforms = ["linux/arm64"]
}

target "sandbox-common" {
  inherits = ["common"]
  context  = "drupal"
  contexts = {
    drupal = "docker-image://${ISLANDORA_REPOSITORY}/drupal:${ISLANDORA_TAG}"
  }
}

###############################################################################
# Default Image targets for local builds.
###############################################################################
target "sandbox" {
  inherits   = ["sandbox-common"]
  cache-from = cacheFrom("sandbox", hostArch())
  tags       = tags("sandbox", "")
}

###############################################################################
# linux/amd64 targets.
###############################################################################
target "sandbox-amd64" {
  inherits   = ["sandbox-common", "amd64-common"]
  cache-from = cacheFrom("sandbox", "amd64")
  tags       = tags("sandbox", "amd64")
}

target "sandbox-amd64-ci" {
  inherits = ["sandbox-amd64"]
  cache-to = cacheTo("sandbox", "amd64")
}

###############################################################################
# linux/arm64 targets.
###############################################################################
target "sandbox-arm64" {
  inherits   = ["sandbox-common", "arm64-common"]
  cache-from = cacheFrom("sandbox", "arm64")
  tags       = tags("sandbox", "arm64")
}

target "sandbox-arm64-ci" {
  inherits = ["sandbox-arm64"]
  cache-to = cacheTo("sandbox", "arm64")
}