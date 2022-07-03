package tasks

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.io.IOUtils
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import plugins.empty
import javax.inject.Inject

// Intentionally not marked as cacheable, because it is more costly than just re-exporting.
open class ExportImages @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    interface Parameters : WorkParameters {
        val image: Property<String>
        val platform: Property<String>
        val dest: DirectoryProperty
    }

    abstract class ExecuteAction @Inject constructor(
        private val execOperations: ExecOperations,
        private val archiveOperations: ArchiveOperations,
        private val fileSystemOperations: FileSystemOperations
    ) : WorkAction<Parameters> {
        private val mapper: JsonMapper
            get() {
                val kotlinModule = KotlinModule.Builder()
                    .configure(KotlinFeature.StrictNullChecks, true)
                    .build()

                return JsonMapper.builder()
                    .addModule(kotlinModule)
                    .build()
            }

        override fun execute() {
            val file =
                parameters.dest.get().asFile.resolve("${FetchManifests.normalizeImageName(parameters.image.get())}.tar")
            // Strip out the port 80 from manifests that refer to the local repository
            val image = parameters.image.get().replace(":80", "")

            // The last fetched image is what is used for "save" so we must always pull before saving to ensure the
            // correct architecture gets saved.
            execOperations.exec {
                commandLine =
                    listOf("docker", "pull", "--platform", parameters.platform.get(), image)
            }.assertNormalExitValue()

            execOperations.exec {
                commandLine =
                    listOf("docker", "save", "-o", file.absolutePath, image)
            }.assertNormalExitValue()

            val unpacked = file.resolveSibling(file.nameWithoutExtension)
            fileSystemOperations.copy {
                from(archiveOperations.tarTree(file))
                into(unpacked)
            }
            file.delete()

            val tag = image
                .split("@")[0]
                .removePrefix("docker.io/")
                .replace("registry.islandora.dev:5000", "islandora")

            val manifest = unpacked.resolve("manifest.json")

            val tags = mapper.createArrayNode().add(tag)
            val root = mapper.readTree(manifest)
            val config = root.filterIsInstance<ObjectNode>().first()
            config.replace("RepoTags", tags)
            mapper.writeValue(manifest, root)

            file.outputStream().use { output ->
                TarOutputStream(output, DEFAULT_BUFFER_SIZE).use { tar ->
                    tar.setLongFileMode(TarOutputStream.LONGFILE_GNU)
                    tar.setBigNumberMode(TarOutputStream.BIGNUMBER_STAR)
                    unpacked
                        .walkTopDown()
                        .forEach { file ->
                            val entry = TarEntry(file, file.relativeTo(unpacked).path)
                            tar.putNextEntry(entry)
                            if (file.isFile) {
                                IOUtils.copy(file.inputStream(), tar)
                            }
                            tar.flush()
                            tar.closeEntry()
                        }
                    tar.finish()
                }
            }
            unpacked.deleteRecursively()
        }
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val src = project.objects.directoryProperty()

    @OutputDirectory
    val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("exports"))

    @TaskAction
    fun execute() {
        dest.empty()
        val workQueue = workerExecutor.noIsolation()
        val outputDirectories = FetchManifests.architectures.associateWith { dest.get().asFile.resolve(it) }
        outputDirectories.forEach { (_, dir) -> dir.mkdir() }
        val images = src.asFileTree.files.flatMap { FetchManifests.Manifest(it).images }
        images.forEach {
            workQueue.submit(ExecuteAction::class.java) {
                this.image.set(it.image)
                this.platform.set(it.platform)
                this.dest.set(outputDirectories[it.architecture]!!)
            }
        }
    }
}