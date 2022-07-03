package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.listProperty
import plugins.execCaptureOutput

open class GenerateCertificates : DefaultTask() {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val executable = project.objects.fileProperty()

    @Input
    val arguments = project.objects.listProperty<String>()

    @Internal
    val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated"))

    @OutputFile
    val cert = project.objects.fileProperty().convention(dest.map { it.file("cert.pem") })

    @OutputFile
    val key = project.objects.fileProperty().convention(dest.map { it.file("privkey.pem") })

    @OutputFile
    val rootCA = project.objects.fileProperty().convention(dest.map { it.file("rootCA.pem") })

    @OutputFile
    val rootCAKey = project.objects.fileProperty().convention(dest.map { it.file("rootCA-key.pem") })

    private val executablePath: String
        get() = executable.get().asFile.absolutePath

    private fun execute(vararg arguments: String) {
        project.exec {
            commandLine = listOf(executablePath) + arguments
            // Exclude JAVA_HOME as we only want to check the local certificates for the system.
            environment = Jvm.current().getInheritableEnvironmentVariables(System.getenv()).filterKeys {
                !setOf("JAVA_HOME").contains(it)
            }
            // Note this is allowed to fail on some systems the user may have to manually install the local certificate.
            // See the README.
            isIgnoreExitValue = true
        }
    }

    private fun install() {
        execute("-install")
        val rootStore = project.file(project.execCaptureOutput(listOf(executablePath, "-CAROOT")))
        listOf(rootCA.get().asFile, rootCAKey.get().asFile).forEach {
            rootStore.resolve(it.name).copyTo(it, true)
        }
    }

    @TaskAction
    fun exec() {
        install()
        execute(
            "-cert-file", cert.get().asFile.absolutePath,
            "-key-file", key.get().asFile.absolutePath,
            *arguments.get().toTypedArray(),
        )
    }
}
