import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import plugins.DownloadsPlugin
import plugins.DownloadsPlugin.DownloadsExtension.Companion.downloads
import tasks.Terraform

apply<DownloadsPlugin>()

downloads {
    val version = "1.2.3"
    fun url(name: String) = "https://releases.hashicorp.com/terraform/${version}/${name}"
    val terraform: Provider<RegularFile> = run {
        val arch = DefaultNativePlatform.getCurrentArchitecture()!!
        val platform = DefaultNativePlatform.getCurrentOperatingSystem()!!
        val download = when {
            platform.isMacOsX && arch.isAmd64 -> create("terraform-darwin-amd64.zip") {
                url = url("terraform_${version}_darwin_amd64.zip")
                sha256 = "2962b0ebdf6f431b8fb182ffc1d8b582b73945db0c3ab99230ffc360d9e297a2"
            }
            platform.isMacOsX && !arch.isAmd64 -> create("terraform-darwin-arm64.zip") {
                url = url("terraform_${version}_darwin_arm64.zip")
                sha256 = "601962205ad3dcf9b1b75f758589890a07854506cbd08ca2fc25afbf373bff53"
            }
            platform.isLinux && arch.isAmd64 -> create("terraform-linux-amd64.zip") {
                url = url("terraform_${version}_linux_amd64.zip")
                sha256 = "728b6fbcb288ad1b7b6590585410a98d3b7e05efe4601ef776c37e15e9a83a96"
            }
            platform.isWindows -> create("terraform-windows-amd64.zip") {
                url = url("terraform_${version}_windows_amd64.zip")
                sha256 = "19773a16263d0873bc86b1109412abb80733524bf6ef7e7290278b2fad33bff6"
            }
            else -> throw RuntimeException("Unsupported platform")
        }
        downloads.getUnpacked(download).map {
            if (platform.isWindows) {
                it.file("terraform.exe")
            } else {
                it.file("terraform")
            }
        }
    }
    tasks.withType<Terraform> {
        executable.convention(terraform.map { it })
    }
}

tasks.withType<Terraform> {
    val token = properties.getOrDefault("digitalocean.token", "") as String
    variables.convention(
        mapOf(
            "image" to "sandbox-${rootProject.version}",
            "token" to token
        )
    )
}

val init by tasks.registering(Terraform::class) {
    val accessKey = properties.getOrDefault("digitalocean.spaces.access_key", "") as String
    val secretKey = properties.getOrDefault("digitalocean.spaces.secret_key", "") as String
    command.set("init")
    arguments.set(
        listOf(
            "-backend-config=access_key=${accessKey}",
            "-backend-config=secret_key=${secretKey}",
        )
    )
}

val plan by tasks.registering(Terraform::class) {
    command.set("plan")
    dependsOn(init)
}

val apply by tasks.registering(Terraform::class) {
    command.set("plan")
    arguments.set(listOf("-auto-approve"))
    dependsOn(init)
}

val destroy by tasks.registering(Terraform::class) {
    command.set("destroy")
    arguments.set(listOf("-auto-approve"))
    dependsOn(init)
}
