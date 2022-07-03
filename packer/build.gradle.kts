import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentArchitecture
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import plugins.DownloadsPlugin
import plugins.DownloadsPlugin.DownloadsExtension.Companion.downloads
import plugins.PackerPlugin
import plugins.PackerPlugin.Packer
import plugins.PackerPlugin.PackerExtension.Companion.packer
import tasks.ExportImages
import tasks.GenerateCertificates

apply<DownloadsPlugin>()
apply<PackerPlugin>()

evaluationDependsOn(":docker")
evaluationDependsOn(":compose:virtualmachine")

val arch = getCurrentArchitecture()!!
val platform = getCurrentOperatingSystem()!!

downloads {
    apply {
        val version = "1.8.2"
        fun url(file: String) = "https://releases.hashicorp.com/packer/$version/$file"
        val packer: Provider<RegularFile> = run {
            val download = when {
                platform.isMacOsX && arch.isAmd64 -> create("packer_darwin_amd64.zip") {
                    url = url("packer_${version}_darwin_amd64.zip")
                    sha256 = "5bb1daa50f503b49bad15c99a1ca90c32d21f3a6c02c5e763980d9b447d71b5d"
                }
                platform.isMacOsX && !arch.isAmd64 -> create("packer_darwin_arm64.zip") {
                    url = url("packer_${version}_darwin_arm64.zip")
                    sha256 = "f70cac04528ccdd4a1c0cafc8bb15b53c342e50e7e903e4b6657119f1c419681"
                }
                platform.isLinux && arch.isAmd64 -> create("packer_linux_amd64.zip") {
                    url = url("packer_${version}_linux_amd64.zip")
                    sha256 = "675bd82561a2e49f89747e092141c7ce79c2e2a9105e6a2ebd49a26df849a468"
                }
                platform.isWindows -> create("packer_windows_amd64.zip") {
                    url = url("packer_${version}_windows_amd64.zip")
                    sha256 = "844cdde6f2e2dbe0d237668ca25bbe7697916bc2e036015b2c1fe54460dfc818"
                }
                else -> throw RuntimeException("Unsupported platform")
            }
            downloads.getUnpacked(download).map {
                if (platform.isWindows) {
                    it.file("packer.exe")
                } else {
                    it.file("packer")
                }
            }
        }
        tasks.withType<Packer> {
            executable.convention(packer.map { it })
        }
    }
    apply {
        fun url(file: String) = "https://github.com/docker/compose/releases/download/v2.6.0/$file"
        val dockerComposeAmd64 by project.extra {
            downloads.get(
                create("docker-compose-linux-x86_64") {
                    url = url(name)
                    sha256 = "4eb9084cd9e33d906bd1ea11b5bc2e77a43f8ffbe7228bcf7c829a7687f5c4bb"
                }
            )
        }
        val dockerComposeArm64 by project.extra {
            downloads.get(
                create("docker-compose-linux-aarch64") {
                    url = url(name)
                    sha256 = "f2bc74dddaa58add7b428b5a764ccd4f048b366f3eb5c80a77ff06fcdc00b3ce"
                }
            )
        }
    }
    apply {
        val version = "35.20220327.3.0"
        fun url(file: String): String {
            val arch = file.split(".")[1]
            val suffix = file.substringAfter("fedora-coreos-")
            return "https://builds.coreos.fedoraproject.org/prod/streams/stable/builds/${version}/${arch}/fedora-coreos-${version}-${suffix}"
        }
        create("fedora-coreos-qemu.aarch64.qcow2.xz") {
            url = url(name)
            sha256 = "d307e22957ba923af7a852619c77a151f5d94636f83e016529c06cf40e911f12"
        }
        create("fedora-coreos-virtualbox.x86_64.ova") {
            url = url(name)
            sha256 = "219f9c58abf1657f0c0300340f22c30aff4e7423db702f36176703aae0fcae3d"
        }
    }
}

packer {
    vars.putAll(mapOf(
        "version" to "${rootProject.version}",
        "revision" to "${rootProject.extra.get("revision")}",
        "token" to "${properties.getOrDefault("digitalocean.token", "")}",
        "packer_ssh_private_key_file" to "${properties.getOrDefault("digitalocean.packer.ssh_private_key_file", "")}",
    ))
    if (platform.isMacOsX && !arch.isAmd64) {
        // Faster builds on the M1
        vars.put("accelerator", "hvf")
    }
    butane {
        from("butane/digital-ocean.yml", "butane/qemu.yml", "butane/virtualbox.yml")
    }
    deploy {
        // Certificates are platform agnostic.
        val certs = project
            .findProject(":certs")!!
            .tasks.named<GenerateCertificates>("generateCertificates")
            .map { task ->
                task.dest.asFileTree.filter { file -> file.extension == "pem" }.files
            }
        from(certs) {
            into("certs")
        }
        // Some files are architecture specific.
        val sandboxExports = project
            .findProject(":docker")!!
            .subprojects.map {
                it.tasks.withType<ExportImages>()
            }
        // Some files are architecture specific.
        val isleExports = project
            .findProject(":compose:virtualmachine")!!
            .tasks.withType<ExportImages>()
        val dockerComposeAmd64: Provider<RegularFile> by project.extra
        val dockerComposeArm64: Provider<RegularFile> by project.extra
        val dockerCompose = mapOf(
            "amd64" to dockerComposeAmd64,
            "arm64" to dockerComposeArm64,
        )
        listOf("amd64", "arm64").forEach { arch ->
            from(dockerCompose[arch]!!) {
                rename("docker-compose.*", "docker-compose")
                into(arch)
            }
            sandboxExports.forEach { task ->
                from(task.map { it.dest.dir(arch) }) {
                    // Custom traefik is only used for desktop/portainer environment.
                    exclude("**/traefik.tar")
                    into("${arch}/images")
                }
            }
            from(isleExports.map { it.dest.dir(arch) }) {
                into("${arch}/images")
            }
        }

        // Some files are shared across platforms.
        rootProject.file(".env").let {
                listOf("digitalocean", "virtualmachine").forEach { platform ->
                    from(it) {
                        into(platform)
                    }
                }
            }
        // Some are platform specific.
        project
            .findProject(":compose:digitalocean")!!
            .files("docker-compose.yml")
            .let {
                from(it) {
                    into("digitalocean")
                }
            }
        project
            .findProject(":compose:virtualmachine")!!
            .files("docker-compose.yml", "tls.yml")
            .let {
                from(it) {
                    into("virtualmachine")
                }
            }
    }
}

tasks.withType<Packer> {
    doFirst {
        if (command.get() == "build") {
            // Delete generated hdd files to allow for rebuilds if failed part way through.
            val sources: List<String> by project.extra
            val explicitSources = arguments.get().filter { sources.contains(it) }
            explicitSources
                .ifEmpty { sources }
                .map { it.split(".").first() }
                .forEach { source ->
                    buildDir.resolve(source).deleteRecursively()
                    when (source) {
                        "virtualbox-ovf" -> {
                            project.exec {
                                commandLine(
                                    "VBoxManage",
                                    "closemedium",
                                    "disk",
                                    buildDir.resolve("virtualbox-ovf/docker.vdi")
                                )
                                isIgnoreExitValue = true
                            }
                        }
                    }
                }
        }
    }
}