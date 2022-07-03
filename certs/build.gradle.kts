import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import plugins.DownloadsPlugin
import plugins.DownloadsPlugin.DownloadsExtension.Companion.downloads
import plugins.DownloadsPlugin.Download
import tasks.GenerateCertificates
import java.nio.file.Files.setPosixFilePermissions
import java.nio.file.attribute.PosixFilePermission.*

apply<DownloadsPlugin>()

val arch = DefaultNativePlatform.getCurrentArchitecture()!!
val platform = DefaultNativePlatform.getCurrentOperatingSystem()!!

downloads {
    val version = "v1.4.4"
    fun url(name: String) = "https://github.com/FiloSottile/mkcert/releases/download/${version}/$name"
    val mkcertDarwinAmd64 = create("mkcert-darwin-amd64") {
        url = url("mkcert-${version}-darwin-amd64")
        sha256 = "a32dfab51f1845d51e810db8e47dcf0e6b51ae3422426514bf5a2b8302e97d4e"
    }
    val mkcertDarwinArm64 = create("mkcert-darwin-arm64") {
        url = url("mkcert-${version}-darwin-arm64")
        sha256 = "c8af0df44bce04359794dad8ea28d750437411d632748049d08644ffb66a60c6"
    }
    val mkcertLinuxAmd64 = create("mkcert-linux-amd64") {
        url = url("mkcert-${version}-linux-amd64")
        sha256 = "6d31c65b03972c6dc4a14ab429f2928300518b26503f58723e532d1b0a3bbb52"
    }
    val mkcertWindowsAmd64 = create("mkcert-windows-amd64.exe") {
        url = url("mkcert-${version}-windows-amd64.exe")
        sha256 = "d2660b50a9ed59eada480750561c96abc2ed4c9a38c6a24d93e30e0977631398"
    }
    val mkcert: Provider<RegularFile> = run {
        downloads.get(when {
            platform.isMacOsX && arch.isAmd64 -> mkcertDarwinAmd64
            platform.isMacOsX && !arch.isAmd64 -> mkcertDarwinArm64
            platform.isLinux && arch.isAmd64 -> mkcertLinuxAmd64
            platform.isWindows -> mkcertWindowsAmd64
            else -> throw RuntimeException("Unsupported platform")
        })
    }
    tasks.withType<GenerateCertificates> {
        executable.convention(mkcert.map { it })
    }
}

tasks.named<Download>("download") {
    doLast {
        if (!platform.isWindows) {
            // Make all downloaded files executable.
            val perms = setOf(
                OWNER_READ,
                OWNER_EXECUTE,
                GROUP_READ,
                GROUP_EXECUTE,
                OTHERS_READ,
                OTHERS_EXECUTE,
            )
            dest.asFileTree.files.forEach {
                setPosixFilePermissions(it.toPath(), perms)
            }
        }
    }
}

tasks.register<GenerateCertificates>("generateCertificates") {
    arguments.set(
        listOf(
            "*.islandora.dev",
            "islandora.dev",
            "localhost",
            "127.0.0.1",
            "::1",
        )
    )
}