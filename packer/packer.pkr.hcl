variable "version" {
  type        = string
  description = "The Islandora Sandbox version."
}

variable "revision" {
  type        = string
  description = "Git repository revision information to store in image metadata."
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

variable "accelerator" {
  type        = string
  default     = "none"
  description = "The accelerator to use when building QEMU image."
}

locals {
  build_directory     = "../build/packer"
  deploy_directory    = "${local.build_directory}/deploy"
  downloads_directory = "${local.build_directory}/downloads"
  unpacked_directory  = "${local.build_directory}/unpacked"
  ignition_directory  = "${local.build_directory}/ignition"
  ova_checksum        = "file:${local.ova_file}.sha256"
  ova_file            = "${local.downloads_directory}/fedora-coreos-virtualbox.x86_64.ova"
  qcow2_checksum      = "file:${local.qcow2_file}.sha256"
  qcow2_file          = "${local.unpacked_directory}/fedora-coreos-qemu.aarch64.qcow2"
}

packer {
  required_plugins {
    digitalocean = {
      version = "~> 1.0"
      source  = "github.com/hashicorp/digitalocean"
    }
    virtualbox = {
      version = "~> 1.0"
      source  = "github.com/hashicorp/virtualbox"
    }
    qemu = {
      version = "~> 1.0"
      source  = "github.com/hashicorp/qemu"
    }
  }
}

source "digitalocean" "sandbox" {
  api_token = var.token
  # Due to API limitations the ID not the image name must be used.
  # This can be found with the following command.
  # doctl -t $TOKEN compute image list
  image                   = "106057111"
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

source "qemu" "sandbox" {
  iso_url          = "${local.qcow2_file}"
  iso_checksum     = "${local.qcow2_checksum}"
  output_directory = "${local.build_directory}/qemu"
  vm_name          = "islandora-sandbox.${var.version}.${formatdate("YYYY-MM-DD", timestamp())}.qcow2"
  qemu_binary      = "qemu-system-aarch64"
  ssh_password     = "password" # Must match the source of the password hash in the ignition file.
  ssh_username     = "core"
  accelerator      = var.accelerator
  cpus             = 8
  disable_vnc      = true
  disk_image       = true
  display          = "none"
  firmware         = "edk2-aarch64-code.fd"
  headless         = true
  machine_type     = "virt,highmem=on"
  memory           = 8192
  disk_size        = "65536M"
  use_pflash       = false
  qemuargs = [
    ["-cpu", "cortex-a72"],
    ["-serial", "stdio"],
    ["-fw_cfg", "name=opt/com.coreos/config,file=${local.ignition_directory}/qemu.ign"],
    ["-nodefaults"],
    ["-nographic"],
  ]
}

source "virtualbox-ovf" "sandbox" {
  source_path          = "${local.ova_file}"
  checksum             = "${local.ova_checksum}"
  format               = "ova"
  guest_additions_mode = "disable"
  output_directory     = "${local.build_directory}/virtualbox-ovf"
  output_filename      = "islandora-sandbox.${var.version}.${formatdate("YYYY-MM-DD", timestamp())}"
  shutdown_command     = "sudo shutdown -P now"
  ssh_password         = "password" # Must match the source of the password hash in the ignition file.
  ssh_username         = "core"
  export_opts = [
    "--manifest",
    "--vsys", "0",
    "--description", "Islandora Sandbox: ${var.revision}",
    "--version", "${var.version}"
  ]
  vboxmanage = [
    ["storagectl", "{{.Name}}", "--name", "AHCI", "--hostiocache", "on", "--portcount", "2"],
    ["createmedium", "disk", "--filename", "${local.build_directory}/virtualbox-ovf/docker.vdi", "--size", "65536", "--format", "VDI"],
    ["storageattach", "{{.Name}}", "--storagectl", "AHCI", "--nonrotational", "on", "--port", "1", "--device", "0", "--type", "hdd", "--medium", "${local.build_directory}/virtualbox-ovf/docker.vdi"],
    ["modifyvm", "{{.Name}}", "--memory", "8192"],
    ["modifyvm", "{{.Name}}", "--cpus", "4"],
    ["modifyvm", "{{.Name}}", "--natpf1", "SSH,tcp,,2222,,22"],
    ["modifyvm", "{{.Name}}", "--natpf1", "HTTPS,tcp,,8443,,8443"],
    ["guestproperty", "set", "{{.Name}}", "/Ignition/Config", file("${local.ignition_directory}/virtualbox.ign")],
  ]
  vboxmanage_post = [
    ["guestproperty", "delete", "{{.Name}}", "/Ignition/Config"],
  ]
  headless = true
}

build {
  sources = [
    "source.digitalocean.sandbox",
    "source.virtualbox-ovf.sandbox",
    "source.qemu.sandbox",
  ]
  provisioner "shell" {
    inline = [
      "sudo mkdir /opt/sandbox",
      "sudo chown core /opt/sandbox"
    ]
  }
  provisioner "file" {
    source      = "${local.deploy_directory}/certs"
    destination = "/opt/sandbox"
  }
  provisioner "file" {
    only        = ["digitalocean.sandbox"]
    source      = "${local.deploy_directory}/digitalocean/"
    destination = "/opt/sandbox"
  }
  provisioner "file" {
    only        = ["virtualbox-ovf.sandbox", "qemu.sandbox"]
    source      = "${local.deploy_directory}/virtualmachine/"
    destination = "/opt/sandbox"
  }
  provisioner "file" {
    only        = ["digitalocean.sandbox", "virtualbox-ovf.sandbox"]
    source      = "${local.deploy_directory}/amd64/"
    destination = "/opt/sandbox"
  }
  provisioner "file" {
    only        = ["qemu.sandbox"]
    source      = "${local.deploy_directory}/arm64/"
    destination = "/opt/sandbox"
  }
  provisioner "shell" {
    inline = [
      "sudo mkdir -p /usr/local/lib/docker/cli-plugins",
      "sudo mv /opt/sandbox/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose",
      "sudo chmod a+x /usr/local/lib/docker/cli-plugins/docker-compose",
      "cd /opt/sandbox/images",
      "ls -1 *.tar | xargs -P8 -n1 bash -c 'docker load -i $0 && rm $0'",
      "rm -fr /opt/sandbox/images",
      "sudo chmod -R a+r /opt/sandbox",
      "sudo chmod a= /opt/sandbox/certs/*",
      "sudo chmod ug+r /opt/sandbox/certs/*",
      "sudo chown root:root /opt/sandbox/certs/*",
    ]
    timeout = "120m"
  }
  provisioner "shell" {
    // We don't do this for QEMU cause it's just too slow to emulate.
    only = ["digitalocean.sandbox", "virtualbox-ovf.sandbox"]
    inline = [
      // Start the services and install Drupal so end-users have a faster startup up time.
      "sudo systemctl start sandbox.service",
      "sleep 30",
      "while ! docker exec drupal test -e /installed; do sleep 10; done",
      "sudo systemctl stop sandbox.service",
    ]
    timeout = "120m"
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

# sudo curl -L --fail https://github.com/docker/compose/releases/download/1.29.2/run.sh -o /usr/local/bin/docker-compose
# sudo chmod +x /usr/local/bin/docker-compose