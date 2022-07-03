plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins.creating {
        id = "CertificatePlugin"
        implementationClass = "plugins.CertificatePlugin"
    }
    plugins.creating {
        id = "DockerPlugin"
        implementationClass = "plugins.DockerPlugin"
    }
    plugins.creating {
        id = "DownloadsPlugin"
        implementationClass = "plugins.DownloadsPlugin"
    }
    plugins.creating {
        id = "PackerPlugin"
        implementationClass = "plugins.PackerPlugin"
    }
}

val antCompress = configurations.create("antCompress")

dependencies {
    antCompress(group = "org.apache.ant", name = "ant-compress", version = "1.5")
    implementation("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", "2.13.3")
    implementation("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.13.3")
    implementation("commons-io", "commons-io", "2.11.0")
    implementation("org.apache.commons", "commons-compress", "1.21")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", "1.5.31")
    implementation("org.tukaani", "xz", "1.9")
    implementation("org.apache.ant", "ant", "1.10.12")
    implementation("com.github.nigelgbanks:isle-docker-plugins:0.10")
    implementation("com.bmuschko:gradle-docker-plugin:7.1.0")
}
