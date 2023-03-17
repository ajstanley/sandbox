variable "version" {
  type        = string
  description = "The Islandora Sandbox version."
}

variable "token" {
  type        = string
  description = "Digital Ocean Authentication token for the Packer User."
  sensitive   = true
}

variable "packer_ssh_private_key_file" {
  type        = string
  description = "Full path the SSH Private Key file for the Packer SSH key in Digital Ocean."
  sensitive   = true
}

locals {
  build_directory    = "build/packer"
  deploy_directory   = "${local.build_directory}/deploy"
  ignition_directory = "${local.build_directory}/ignition"
}

packer {
  required_plugins {
    digitalocean = {
      version = "~> 1.0"
      source  = "github.com/hashicorp/digitalocean"
    }
  }
}

source "digitalocean" "sandbox" {
  api_token = var.token
  # Due to API limitations the ID not the image name must be used.
  # This can be found with the following command.
  # doctl -t $TOKEN compute image list
  image                   = "127958764"
  region                  = "tor1"
  size                    = "s-4vcpu-8gb-intel"
  droplet_name            = "packer"
  snapshot_name           = "sandbox-${var.version}"
  snapshot_regions        = ["tor1"]
  ssh_username            = "core"
  ssh_key_id              = 34230062                        # doctl -t ${TOKEN} compute ssh-key list
  ssh_private_key_file    = var.packer_ssh_private_key_file # User must specify the location of this file in ~/.gradle/gradle.properties, check bitwarden for the value.
  temporary_key_pair_type = "ed25519"
  pause_before_connecting = "5s"
  user_data_file          = "${local.ignition_directory}/digital-ocean.ign"
}

build {
  sources = [
    "source.digitalocean.sandbox",
  ]
  provisioner "shell" {
    inline = [
      "sudo mkdir -p /opt/sandbox/acme",
      "sudo chown core /opt/sandbox"
    ]
  }
  provisioner "file" {
    source      = "${local.deploy_directory}/"
    destination = "/opt/sandbox"
  }
  provisioner "shell" {
    inline = [
      "sudo mkdir -p /usr/local/lib/docker/cli-plugins",
      "sudo curl -SL https://github.com/docker/compose/releases/download/v2.16.0/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose",
      "sudo chmod a+x /usr/local/lib/docker/cli-plugins/docker-compose",
      "sudo chmod -R a+r /opt/sandbox",
      "cd /opt/sandbox && sudo docker compose pull",
    ]
  }
}

#post-processor "digitalocean-import" {

#  api_token         = var.token
#  spaces_key        = "{{user `key`}}"
#  spaces_secret     = "{{user `secret`}}"
#  spaces_region     = "tor1"
#  space_name        = "import-bucket"
#  image_name        = "sandbox"
#  image_description = "Islandora Sandbox: {{timestamp}}"
#  image_regions     = ["tor1"]
#  image_tags        = ["coreos", "custom", "packer", "sandbox"]
#}