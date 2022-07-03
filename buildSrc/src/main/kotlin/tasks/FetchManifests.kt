package tasks

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import plugins.empty
import java.io.File
import javax.inject.Inject

@CacheableTask
open class FetchManifests @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    companion object {
        public val platforms = listOf("linux/amd64", "linux/arm64")
        public val architectures = platforms.map { it.substringAfter("/") }

        fun normalizeImageName(image: String): String {
            return """(.*/)?(sandbox-)?([^:]*):.*""".toRegex().replace(image, "$3")
        }
    }

    class Manifest(file: File) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class Platform(val architecture: String, val os: String)

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class Descriptor(val digest: String, val platform: Platform)

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class Entry(val Ref: String, val Descriptor: Descriptor)

        data class Image(val image: String, val architecture: String, val os: String) {
            val platform: String
                get() = "${os}/${architecture}"
        }

        val images: List<Image>

        init {
            val kotlinModule = KotlinModule.Builder()
                .configure(KotlinFeature.StrictNullChecks, true)
                .build()

            val mapper: ObjectMapper = JsonMapper.builder()
                .addModule(kotlinModule)
                .build()

            val entries: List<Entry> = mapper.readValue(file.readText())

            images = entries
                .filterNot {
                    val (architecture, os) = it.Descriptor.platform
                    (os != "linux") || !architectures.contains(architecture)
                }
                .map {
                    val (architecture, os) = it.Descriptor.platform
                    Image("${it.Ref.split("@")[0]}@${it.Descriptor.digest}", architecture, os)
                }
        }
    }

    interface Parameters : WorkParameters {
        val image: Property<String>
        val dest: DirectoryProperty
    }

    // The implementation of a single unit of work.
    abstract class ExecuteAction @Inject constructor(private val execOperations: ExecOperations) :
        WorkAction<Parameters> {
        override fun execute() {
            val file = parameters.dest.get().asFile.resolve("${normalizeImageName(parameters.image.get())}.json")
            file.outputStream().use { output ->
                execOperations.exec {
                    standardOutput = output
                    commandLine =
                        listOf("docker", "manifest", "inspect", "--verbose", "--insecure", parameters.image.get())
                }
            }
        }
    }

    @Input
    val images = project.objects.listProperty<String>()

    @OutputDirectory
    val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("manifests"))

    @TaskAction
    fun execute() {
        dest.empty()
        val workQueue = workerExecutor.noIsolation()
        images.get().forEach { image ->
            workQueue.submit(ExecuteAction::class.java) {
                this.image.set(image)
                this.dest.set(this@FetchManifests.dest)
            }
        }
    }
}