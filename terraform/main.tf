terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.0"
    }
  }
  backend "s3" {
    key                         = "state/terraform.tfstate"
    bucket                      = "islandora-sandbox-terraform"
    region                      = "us-west-1"
    endpoint                    = "https://nyc3.digitaloceanspaces.com"
    skip_region_validation      = true
    skip_credentials_validation = true
    skip_metadata_api_check     = true
  }
}

provider "digitalocean" {
  token = var.token
}

data "digitalocean_reserved_ip" "sandbox" {
  // Should match the wildcard A record in Hover.
  ip_address = "159.203.49.92"
}

data "digitalocean_reserved_ip" "test" {
  // Should match the wildcard A record in Hover.
  ip_address = "174.138.114.200"
}

data "digitalocean_ssh_keys" "keys" {
  filter {
    key    = "name"
    values = [
      "dannylamb",
      "Nigel Banks",
      "packer",
    ]
  }
}

data "digitalocean_image" "sandbox" {
  name = var.image
}

resource "digitalocean_droplet" "sandbox" {
  image    = data.digitalocean_image.sandbox.id
  name     = "sandbox"
  region   = "tor1"
  size     = "s-4vcpu-8gb"
  ssh_keys = data.digitalocean_ssh_keys.keys.ssh_keys[*].id
}

resource "digitalocean_reserved_ip_assignment" "sandbox" {
  ip_address = data.digitalocean_reserved_ip.sandbox.ip_address
  droplet_id = digitalocean_droplet.sandbox.id
}
