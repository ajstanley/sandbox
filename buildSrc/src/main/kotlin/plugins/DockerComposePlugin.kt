package plugins

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import tasks.GenerateCertificates

class DockerComposePlugin : Plugin<Project> {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Build(val context: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Service(val image: String?, val build: Build?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Config(val services: Map<String, Service>) {
        companion object {
            fun fromFile(file: RegularFile): Config {
                val kotlinModule = KotlinModule.Builder()
                    .configure(KotlinFeature.StrictNullChecks, true)
                    .build()

                val mapper: ObjectMapper = YAMLMapper.builder()
                    .addModule(kotlinModule)
                    .build()

                return mapper.readValue(file.asFile)
            }
        }
    }

    @CacheableTask
    open class DockerCompose : DefaultTask() {
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        val envFile = project.objects.fileProperty()

        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        val composeFile =
            project.objects.fileProperty().convention(
                project.layout.projectDirectory.file("docker-compose.yml")
            )

        @Input
        val environment = project.objects.mapProperty<String, String>()

        @Input
        val options = project.objects.listProperty<String>()

        @Input
        val command = project.objects.property<String>()

        @Input
        val arguments = project.objects.listProperty<String>()

        @OutputFile
        @Optional
        val outputFile = project.objects.fileProperty()

        init {
            outputs.cacheIf {
                (it as DockerCompose).outputFile.isPresent
            }
            outputs.upToDateWhen {
                // Always out of date if no output file is specified.
                (it as DockerCompose).outputFile.isPresent
            }
            // Assign .env if found in directory or parent directory.
            generateSequence(project.layout.projectDirectory, { it.dir("..") })
                .map {
                    it.file(".env")
                }.find {
                    it.asFile.exists()
                }?.let {
                    envFile.convention(it)
                }
        }

        @TaskAction
        fun exec() {
            project.exec {
                environment(this@DockerCompose.environment.get())
                commandLine = mutableListOf("docker", "compose").apply {
                    addAll(options.get())
                    addAll(
                        listOf(
                            "--env-file", envFile.get().asFile.absolutePath,
                            "--file", composeFile.get().asFile.absolutePath,
                        )
                    )
                    if (logger.isEnabled(LogLevel.INFO)) {
                        add("--verbose")
                    }
                    add(command.get())
                    addAll(arguments.get())
                }
                if (outputFile.isPresent) {
                    standardOutput = outputFile.get().asFile.outputStream()
                }
            }
        }
    }

    override fun apply(project: Project): Unit = project.run {
        val profiles by tasks.registering(DockerCompose::class) {
            command.set("config")
            arguments.add("--profiles")
            outputFile.set(layout.buildDirectory.file("profiles.txt"))
        }

        val profileOptions = profiles.map {
            it.outputFile.get().asFile.readLines().map {
                listOf("--profile", it)
            }.flatten()
        }

        val config by tasks.registering(DockerCompose::class) {
            options.addAll(profileOptions)
            command.set("config")
            outputFile.set(layout.buildDirectory.file("config.yml"))
        }

        val pull by tasks.registering(DockerCompose::class) {
            options.addAll(profileOptions)
            command.set("pull")
            // Some images are built locally.
            arguments.add("--ignore-pull-failures")
            outputs.upToDateWhen { false }
        }

        val build by tasks.registering(DockerCompose::class) {
            command.set("build")
            arguments.set(config.map {
                Config.fromFile(it.outputFile.get())
                    .services
                    .filter { (_, service) ->
                        service.build !== null
                    }.map { (name, _) ->
                        name
                    }
            })
        }

        val down by tasks.registering(DockerCompose::class) {
            environment.put("COMPOSE_PROJECT_NAME", "sandbox-${project.name}")
            options.addAll(profileOptions)
            command.set("down")
            arguments.add("-v")
        }

        val wait by tasks.registering(DockerCompose::class) {
            environment.put("COMPOSE_PROJECT_NAME", "sandbox-${project.name}")
            command.set("exec")
            arguments.set(listOf(
                "drupal", "timeout", "600", "bash", "-c", "while ! test -f /installed; do sleep 5; done"
            ))
        }

        tasks.register<DockerCompose>("up") {
            environment.put("COMPOSE_PROJECT_NAME", "sandbox-${project.name}")
            command.set("up")
            arguments.add("-d")
            dependsOn(pull, build)
            mustRunAfter(down)
            finalizedBy(wait)
        }

        tasks.withType<DockerCompose>() {
            val generateCertificates =
                rootProject.project(":certs").tasks.named<GenerateCertificates>("generateCertificates")

            // Environments like "portainer" seek to have no bind mounts so all users require is a single
            // docker compose file.
            environment.putAll(
                generateCertificates.map {
                    fun certificateContents(filename: String) = it.dest.file(filename).get().asFile.readText()
                    mapOf(
                        "CERT_PUBLIC_KEY" to certificateContents("cert.pem"),
                        "CERT_PRIVATE_KEY" to certificateContents("privkey.pem"),
                        "CERT_AUTHORITY" to certificateContents("rootCA.pem"),
                    )
                }

            )
            // The virtualmachine environments need to be able bind mount certificates as secrets since users will
            // upload certificates after starting the server.
            environment.putAll(
                generateCertificates.map {
                    fun certificateFile(filename: String) = it.dest.file(filename).get().asFile.absolutePath
                    mapOf(
                        "CERT_PUBLIC_KEY_FILE" to certificateFile("cert.pem"),
                        "CERT_PRIVATE_KEY_FILE" to certificateFile("privkey.pem"),
                        "CERT_AUTHORITY_FILE" to certificateFile("rootCA.pem"),
                    )
                }
            )
            dependsOn(generateCertificates)
        }
    }
}