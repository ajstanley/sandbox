# Include docker-compose environment variables.
include .env

# Display help by default.
.DEFAULT_GOAL := help

# Require bash to use foreach loops.
SHELL := bash

# For text display in the shell.
RESET = $(shell tput sgr0)
RED = $(shell tput setaf 9)
BLUE = $(shell tput setaf 6)
TARGET_MAX_CHAR_NUM = 30

# The location of root certificates.
CAROOT = $(shell mkcert -CAROOT)

# Display text for requirements.
README_MESSAGE = ${BLUE}Consult the README.md for how to install requirements.${RESET}\n

# Bash snippet to check for the existance an executable.
define executable-exists
	@if ! command -v $(1) >/dev/null; \
	then \
		printf "${RED}Could not find executable: %s${RESET}\n${README_MESSAGE}" $(1); \
		exit 1; \
	fi
endef

# Used to include host-platform specific docker compose files.
OS := $(shell uname -s | tr A-Z a-z)

# The buildkit builder to use.
BUILDER ?= default

# Were to push/pull from.
REPOSITORY ?= islandora

# Were to push/pull cache from.
CACHE_FROM_REPOSITORY ?= $(REPOSITORY)
CACHE_TO_REPOSITORY ?= $(REPOSITORY)

# Tag to apply to all images loaded or pushed.
TAG ?= local

# Targets in `docker-bake.hcl` to build if requested.
TARGET ?= default

# Used as the base image of Digital Ocean Droplet.
COREOS_VERSION=37.20230205.3.0

# Typical location for packer ssh key (See Bitwarden for the file).
PACKER_SSH_PRIVATE_KEY=$(shell echo "$${HOME}")/.ssh/packer

# This is a catch all target that is used to check for existance of an
# executable when declared as a dependency.
.PHONY: %
%:
	$(call executable-exists,$@)

# Checks for docker compose plugin.
.PHONY: docker-compose
docker-compose: MISSING_DOCKER_PLUGIN_MESSAGE = ${RED}docker compose plugin is not installed${RESET}\n${README_MESSAGE}
docker-compose: | docker
  # Check for `docker compose` as compose version 2+ is used is assumed.
	@if ! docker compose version &>/dev/null; \
	then \
		printf "$(MISSING_DOCKER_PLUGIN_MESSAGE)"; \
		exit 1; \
	fi

# Checks for docker buildx plugin.
.PHONY: docker-buildx
docker-buildx: MISSING_DOCKER_BUILDX_PLUGIN_MESSAGE = ${RED}docker buildx plugin is not installed${RESET}\n${README_MESSAGE}
docker-buildx: | docker
  # Check for `docker buildx` as we do not support building without it.
	@if ! docker buildx version &>/dev/null; \
	then \
		printf "$(MISSING_DOCKER_BUILDX_PLUGIN_MESSAGE)"; \
		exit 1; \
	fi

.git/hooks/pre-commit: | pre-commit
.git/hooks/pre-commit:
	pre-commit install

build:
	mkdir -p build

.PHONY: login
login: REGISTRIES = https://index.docker.io/v1/
login: | docker jq
login:
	@for registry in $(REGISTRIES); \
	do \
		if ! jq -e ".auths|keys|any(. == \"$$registry\")" ~/.docker/config.json &>/dev/null; \
		then \
			printf "Log into $$registry\n"; \
			docker login $$registry; \
		fi \
	done

$(CAROOT)/rootCA-key.pem $(CAROOT)/rootCA.pem &: | mkcert
  # Requires mkcert to be installed first (It may fail on some systems due to how Java is configured, but this can be ignored).
	-mkcert -install

# Using mkcert to generate local certificates rather than traefik certs
# as they often get revoked.
build/certs/cert.pem build/certs/privkey.pem build/certs/rootCA.pem build/certs/rootCA-key.pem  &: $(CAROOT)/rootCA-key.pem $(CAROOT)/rootCA.pem | mkcert build
	mkdir -p build/certs
	mkcert -cert-file build/certs/cert.pem -key-file build/certs/privkey.pem \
		"*.islandora.io" \
		"islandora.io" \
		"*.islandora.io" \
		"islandora.io" \
		"*.islandora.info" \
		"islandora.info" \
		"localhost" \
		"127.0.0.1" \
		"::1"
	cp "$(CAROOT)/rootCA-key.pem" build/certs/rootCA-key.pem
	cp "$(CAROOT)/rootCA.pem" build/certs/rootCA.pem

build/certs/tls.crt: build/certs/rootCA.pem
	cp build/certs/rootCA.pem build/certs/tls.crt

build/certs/tls.key: build/certs/rootCA-key.pem
	cp build/certs/rootCA-key.pem build/certs/tls.key

.PHONY: certs
## Generate certificates required for using docker compose.
certs: build/certs/tls.crt build/certs/tls.key

# Prior to building we export the plan including variables from provided by the
# environment etc.
# Despite being a real target we make it PHONY so it is run everytime as $(TARGET) can change.
.PHONY: build/bake.json
.SILENT: build/bake.json
build/bake.json: | docker-buildx jq build
  # Generate build plan for the given target and update the contexts if provided by the CI.
	CACHE_FROM_REPOSITORY=$(CACHE_FROM_REPOSITORY) \
	CACHE_TO_REPOSITORY=$(CACHE_TO_REPOSITORY) \
	ISLANDORA_REPOSITORY=$(ISLANDORA_REPOSITORY) \
	ISLANDORA_TAG=$(ISLANDORA_TAG) \
	REPOSITORY=$(REPOSITORY) \
	TAG=$(TAG) \
	docker buildx bake --print $(TARGET) 2>/dev/null > build/bake.json;

.SILENT: build/manifests.json
build/manifests.json: build/bake.json
	jq '[.target[].tags[]] | reduce .[] as $$i ({}; .[$$i | sub("-(arm64|amd64)$$"; "")] = ([$$i] + .[$$i | sub("-(arm64|amd64)$$"; "")] | sort))' build/bake.json > build/manifests.json

.PHONY: bake
## Builds and loads the target(s) into the local docker context.
bake: build/bake.json
	docker buildx bake --builder $(BUILDER) -f build/bake.json --load

.PHONY: push
## Builds and pushes the target(s) into remote repository.
push: build/bake.json login
push:
	docker buildx bake --builder $(BUILDER) -f build/bake.json --push

.PHONY: manifest
## Creates manifest for multi-arch images.
manifest: build/manifests.json $(filter push,$(MAKECMDGOALS)) | jq
  # Since this is only really used by the Github Actions it's built to assume a single target at a time.
	MANIFESTS=(); \
	while IFS= read -r line; do \
			MANIFESTS+=( "$$line" ); \
	done < <(jq -r '. | to_entries | reduce .[] as $$i ([]; . + ["\($$i.key) \($$i.value | join(" "))"]) | .[]' build/manifests.json); \
	for args in "$${MANIFESTS[@]}"; \
	do \
		docker buildx imagetools create -t $${args}; \
	done
  # After creating the manifests we can fetch the digests to use as contexts in later builds.
	DIGESTS=(); \
	while IFS= read -r line; do \
		DIGESTS+=( "$$line" ); \
	done < <(jq -r 'keys | reduce .[] as $$i ({}; .[$$i | sub("^[^/]+/(?<x>[^@:]+).*$$"; "\(.x)")] = $$i) | to_entries[] | "\(.key) \(.value)"' build/manifests.json); \
	for digest in "$${DIGESTS[@]}"; \
	do \
		args=($${digest}); \
		context=$${args[0]}; \
		image=$${args[1]}; \
		docker buildx imagetools inspect --raw $${image} | shasum -a 256 | cut -f1 -d' ' | tr -d '\n' > build/$${context}.digest; \
	done

.PHONY: up
## Starts up the local development environment.
up: $(filter down,$(MAKECMDGOALS))
up: bake | docker-compose
	docker compose up -d
	@printf "Waiting for installation..."
	@docker compose exec drupal timeout 600 bash -c "while ! test -f /installed; do sleep 5; done"
	@printf "  Credentials:\n"
	@printf "  ${RED}%-$(TARGET_MAX_CHAR_NUM)s${RESET} ${BLUE}%s${RESET}\n" "Username" "admin"
	@printf "  ${RED}%-$(TARGET_MAX_CHAR_NUM)s${RESET} ${BLUE}%s${RESET}\n" "Password" "password"
	@printf "\n  Services Available:\n"
	@for link in \
		"Drupal|http://islandora.io" \
		"ActiveMQ|http://activemq.islandora.io" \
		"Blazegraph|http://blazegraph.islandora.io/bigdata/" \
		"Fedora|http://fcrepo.islandora.io/fcrepo/rest/" \
		"Matomo|http://islandora.io/matomo/index.php" \
		"Cantaloupe|http://islandora.io/cantaloupe" \
		"Solr|http://solr.islandora.io" \
		"Traefik|http://traefik.islandora.io" \
	; \
	do \
		echo $$link | tr -s '|' '\000' | xargs -0 -n2 printf "  ${RED}%-$(TARGET_MAX_CHAR_NUM)s${RESET} ${BLUE}%s${RESET}"; \
	done

.PHONY: stop
## Stops the local development environment.
stop: | docker-compose
	docker compose down

.PHONY: down
## Stops the local development environment and destroys volumes.
down: | docker-compose
	docker compose down -v

# Marked as phony
build/buildkitd.toml: build/certs/cert.pem build/certs/privkey.pem build/certs/rootCA.pem
	@contents=( \
	"[worker.containerd]" \
	"  enabled = false" \
	"[worker.oci]" \
	"  enabled = true" \
	"  gc = false" \
	"[registry.\"islandora.io\"]" \
	"  insecure=false" \
	"  ca=[\"$(CURDIR)/build/certs/rootCA.pem\"]" \
	"  [[registry.\"islandora.io\".keypair]]" \
	"    key=\"$(CURDIR)/build/certs/privkey.pem\"" \
	"    cert=\"$(CURDIR)/build/certs/cert.pem\"" \
	) && \
	printf '%s\n' "$${contents[@]}" >build/buildkitd.toml

.PHONY: network-create
network-create: | docker
	if ! docker network inspect isle-sandbox &>/dev/null; \
	then \
		docker network create isle-sandbox; \
	fi

.PHONY: network-destroy
network-destroy: | docker
	if docker network inspect isle-sandbox &>/dev/null; \
	then \
		docker network rm isle-sandbox; \
	fi

.PHONY: registry-create
registry-create: network-create build/certs/cert.pem build/certs/privkey.pem build/certs/rootCA.pem | docker
	if ! docker volume inspect isle-sandbox-registry &>/dev/null; \
	then \
		docker volume create isle-sandbox-registry; \
	fi
	if ! docker container inspect isle-sandbox-registry &>/dev/null; \
	then \
		docker create \
			--network isle-sandbox \
			--network-alias islandora.io \
			--env "REGISTRY_HTTP_ADDR=0.0.0.0:443" \
			--env "REGISTRY_STORAGE_DELETE_ENABLED=true" \
			--env "REGISTRY_HTTP_TLS_CERTIFICATE=/usr/local/share/ca-certificates/cert.pem" \
			--env "REGISTRY_HTTP_TLS_KEY=/usr/local/share/ca-certificates/privkey.pem" \
			--volume "$(CURDIR)/build/certs/cert.pem:/usr/local/share/ca-certificates/cert.pem:ro" \
			--volume "$(CURDIR)/build/certs/privkey.pem:/usr/local/share/ca-certificates/privkey.pem:ro" \
			--volume "$(CURDIR)/build/certs/rootCA.pem:/usr/local/share/ca-certificates/rootCA.pem:ro" \
			--volume isle-sandbox-registry:/var/lib/registry \
			--name isle-sandbox-registry \
			registry:2; \
	fi
	docker start isle-sandbox-registry

.PHONY: registry-stop
registry-stop: | docker
	if docker container inspect isle-sandbox-registry &>/dev/null; \
	then \
		docker stop isle-sandbox-registry >/dev/null; \
	fi

.PHONY: registry-destroy
registry-destroy: registry-stop | docker
	if docker container inspect isle-sandbox-registry &>/dev/null; \
	then \
		docker rm isle-sandbox-registry; \
	fi
	if docker volume inspect isle-sandbox-registry &>/dev/null; \
		then \
			docker volume rm isle-sandbox-registry >/dev/null; \
	fi

.PHONY: builder-create
builder-create: build/buildkitd.toml registry-create | docker-buildx
	if ! docker buildx inspect isle-sandbox &>/dev/null; \
	then \
		docker buildx create \
			--append \
			--bootstrap \
			--config build/buildkitd.toml \
			--driver-opt "image=moby/buildkit:v0.11.1,network=isle-sandbox" \
			--name "isle-sandbox"; \
	fi

.PHONY: builder-destroy
builder-destroy: | docker-buildx
	if docker buildx inspect isle-sandbox &>/dev/null; \
	then \
		docker buildx rm "isle-sandbox"; \
	fi
