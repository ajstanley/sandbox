package tasks

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property

open class Terraform : DefaultTask() {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val executable = project.objects.fileProperty()

    @Input
    val command = project.objects.property<String>()

    @Input
    val variables = project.objects.mapProperty<String, String>()

    @Input
    val arguments = project.objects.listProperty<String>()

    private val executablePath: String
        get() = executable.get().asFile.absolutePath

    @TaskAction
    fun exec() {
        val vars = variables.get().map { (key, value) -> listOf("-var", "$key=$value") }.flatten()
        project.exec {
            workingDir = project.projectDir
            commandLine = listOf(executablePath, command.get()) + vars + arguments.get()
            if (logger.isEnabled(LogLevel.INFO)) {
                environment("TF_LOG", "1")
            }
        }
    }
}
