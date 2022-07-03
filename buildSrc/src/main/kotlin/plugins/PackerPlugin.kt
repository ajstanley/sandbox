package plugins

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import java.util.regex.Pattern
import javax.inject.Inject


class PackerPlugin : Plugin<Project> {

    // Compiles Butane configuration and generates Ignition configuration for provisioning CoreOS.
    // https://coreos.github.io/butane/
    // https://coreos.github.io/ignition/
    @CacheableTask
    open class CompileIgnition : DefaultTask() {
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        val configs = project.objects.fileCollection()

        @OutputDirectory
        val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("ignition"))

        init {
            description = "Compiles butane configurations into ignition configuration."
        }

        @TaskAction
        fun exec() {
            dest.empty()
            configs.forEach { file ->
                file.inputStream().use { input ->
                    dest.get()
                        .file("${file.nameWithoutExtension}.ign")
                        .asFile
                        .outputStream()
                        .use { output ->
                            project.exec {
                                standardInput = input
                                standardOutput = output
                                commandLine = listOf(
                                    "docker",
                                    "run",
                                    "--rm",
                                    "-i",
                                    "quay.io/coreos/butane:v0.14.0",
                                    "--pretty",
                                    "--strict",
                                )
                            }
                        }
                }
            }
        }
    }

    open class Packer : DefaultTask() {
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        val executable = project.objects.fileProperty()

        @Input
        val command = project.objects.property<String>()

        @Input
        val arguments = project.objects.listProperty<String>()

        @Input
        val variables = project.objects.mapProperty<String, String>()

        @Internal
        val sources = project.objects.listProperty<String>()

        private val executablePath: String
            get() = executable.get().asFile.absolutePath

        @TaskAction
        fun exec() {
            val vars = variables.get().map { (key, value) -> listOf("-var", "$key=$value") }.flatten()
            project.exec {
                workingDir = project.projectDir
                commandLine = listOf(executablePath, command.get()) + vars + arguments.get() + listOf(".")
                if (logger.isEnabled(LogLevel.INFO)) {
                    environment("PACKER_LOG", "1")
                }
            }
        }
    }

    open class PackerExtension @Inject constructor(objects: ObjectFactory, operations: FileOperations) {
        val vars = objects.mapProperty<String, String>()
        internal val butane = objects.fileCollection()
        internal val deploy = operations.copySpec()

        fun butane(action: Action<ConfigurableFileCollection>) {
            action.execute(butane)
        }

        fun deploy(action: Action<CopySpec>) {
            action.execute(deploy)
        }

        companion object {
            fun Project.packer(action: Action<PackerExtension>) {
                action.execute(extensions.getByName<PackerExtension>("packer"))
            }
        }
    }

    private fun normalizeSource(source: String) = source.split(".")
        .first()
        .split("-")
        .joinToString("") {
            it.capitalize()
        }

    override fun apply(project: Project): Unit = project.run {
        execCaptureOutput(listOf("packer", "init", "."))

        val packer = extensions.create<PackerExtension>("packer")
        val inspect = execCaptureOutput(listOf("packer", "inspect", "."))

        val sources by extra {
            Pattern.compile("""sources:(.*)provisioners:""", Pattern.MULTILINE or Pattern.DOTALL)
                .matcher(inspect)
                .results()
                .findFirst()
                .get()
                .group(1)
                .replace("""[ \t]""".toRegex(), "")
                .lines()
                .filterNot { it.isEmpty() }
        }

        val compileIgnition by tasks.registering(CompileIgnition::class) {
            configs.from(packer.butane.files)
        }

        val deploy by tasks.registering(Sync::class) {
            with(packer.deploy)
            into(buildDir.resolve("deploy"))
        }

        sources.forEach { source ->
            tasks.register<Packer>("build${normalizeSource(source)}") {
                command.set("build")
                variables.set(packer.vars)
                arguments.addAll("-force", "--only", source)
                dependsOn(deploy, compileIgnition)
            }
        }
    }
}