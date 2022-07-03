package plugins

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import java.io.ByteArrayOutputStream

fun Project.execCaptureOutput(command: List<String>, error: String = "Failed to execute") =
    ByteArrayOutputStream().use { output ->
        val result = this.exec {
            workingDir = projectDir
            commandLine = command
            standardOutput = output
        }
        if (result.exitValue != 0) throw RuntimeException(error)
        output.toString()
    }.trim()

fun DirectoryProperty.empty(): DirectoryProperty = apply {
    get().asFile.run {
        deleteRecursively()
        mkdir()
    }
}