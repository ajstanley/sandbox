package plugins

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.internal.hash.Hashing
import org.gradle.kotlin.dsl.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.tukaani.xz.XZInputStream
import plugins.DownloadsPlugin.GenerateWaybills.WayBill
import java.io.File
import java.io.Serializable
import java.net.URI
import javax.inject.Inject

class DownloadsPlugin : Plugin<Project> {

    @CacheableTask
    open class GenerateWaybills : DefaultTask() {

        data class WayBill(val url: String, val sha256: String, val filename: String) : Serializable

        @Input
        val waybills = project.objects.listProperty<WayBill>()

        @OutputDirectory
        val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("waybills"))

        @TaskAction
        fun exec() {
            dest.empty()
            val dest = dest.get().asFile
            waybills.get().forEach { bill ->
                val objectMapper = ObjectMapper()
                objectMapper.writeValue(dest.resolve(bill.filename), bill)
            }
        }
    }

    @CacheableTask
    open class Download @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

        interface Parameters : WorkParameters {
            val waybill: Property<WayBill>
            val dest: DirectoryProperty
            val root: DirectoryProperty
        }

        abstract class ExecuteAction @Inject constructor() :
            WorkAction<Parameters> {

            private fun hash(file: File) = Hashing.sha256().hashFile(file).toString()

            override fun execute() {
                val waybill = parameters.waybill.get()
                val dest = parameters.dest.get().asFile
                val root = parameters.root.get().asFile

                val download = dest.resolve(waybill.filename)
                FileUtils.copyURLToFile(URI(waybill.url).toURL(), download)

                val sha256 = hash(download)
                if (sha256 != waybill.sha256)
                    throw GradleException("Checksum does not match. Expected: ${waybill.sha256}, Calculated: $sha256")

                download.resolveSibling("${waybill.filename}.sha256")
                    .writeText("${sha256}\t${download.relativeTo(root)}\n")
            }
        }

        @InputDirectory
        @PathSensitive(PathSensitivity.RELATIVE)
        val src = project.objects.directoryProperty()

        @OutputDirectory
        val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("downloads"))

        private val mapper: ObjectMapper
            get() {
                val kotlinModule = KotlinModule.Builder()
                    .configure(KotlinFeature.StrictNullChecks, true)
                    .build()

                return JsonMapper.builder()
                    .addModule(kotlinModule)
                    .build()
            }

        private fun parse(file: File) = mapper.readValue(file, WayBill::class.java)

        @TaskAction
        fun execute() {
            dest.empty()
            val workQueue = workerExecutor.noIsolation()
            val waybills: List<WayBill> = src.asFileTree.files.map { parse(it) }
            waybills.forEach { waybill ->
                workQueue.submit(ExecuteAction::class.java) {
                    this.waybill.set(waybill)
                    this.dest.set(this@Download.dest)
                    this.root.set(project.rootProject.layout.projectDirectory)
                }
            }
        }
    }

    open class Unpack @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

        interface Parameters : WorkParameters {
            val src: RegularFileProperty
            val dest: DirectoryProperty
            val root: DirectoryProperty
        }

        abstract class ExecuteAction @Inject constructor(
            private val fileSystemOperations: FileSystemOperations,
            private val archiveOperations: ArchiveOperations
        ) :
            WorkAction<Parameters> {

            private fun hash(file: File) = Hashing.sha256().hashFile(file).toString()

            override fun execute() {
                val src = parameters.src.get().asFile
                val dest = parameters.dest.get().asFile
                val root = parameters.root.get().asFile

                // Unpack if applicable.
                when (src.extension) {
                    "xz" -> {
                        val unpacked = dest.resolve(src.nameWithoutExtension)
                        IOUtils.copyLarge(XZInputStream(src.inputStream(), 1024 * 256), unpacked.outputStream())
                        unpacked.resolveSibling("${unpacked.name}.sha256")
                            .writeText("${hash(unpacked)}\t${unpacked.relativeTo(root)}\n")
                    }
                    "zip" -> {
                        // To prevent collisions when unpacking multiple zip files each gets unzip into it's own
                        // directory.
                        @Suppress("NAME_SHADOWING") val dest = dest.resolve(src.name)
                        dest.mkdir()
                        fileSystemOperations.copy {
                            from(archiveOperations.zipTree(src))
                            into(dest)
                        }
                        dest.walk()
                            .filter { it.isFile }
                            .forEach {
                                it.resolveSibling("${it.name}.sha256")
                                    .writeText("${hash(it)}\t${it.relativeTo(root)}\n")
                            }
                    }
                }
            }
        }

        @InputDirectory
        @PathSensitive(PathSensitivity.RELATIVE)
        val src = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("downloads"))

        @OutputDirectory
        val dest = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("unpacked"))

        @TaskAction
        fun execute() {
            dest.empty()
            val workQueue = workerExecutor.noIsolation()
            val supportedExtensions = listOf("xz", "zip")
            val archives: List<File> = src.asFileTree.files
                .filter { supportedExtensions.contains(it.extension) }
            archives.forEach { archive ->
                workQueue.submit(ExecuteAction::class.java) {
                    this.src.set(archive)
                    this.dest.set(this@Unpack.dest)
                    this.root.set(project.rootProject.layout.projectDirectory)
                }
            }
        }
    }

    open class DownloadHandler @Inject constructor(
        private val name: String,
        objects: ObjectFactory
    ) : Named {
        private val _url = objects.property<String>()
        private val _sha256 = objects.property<String>()

        var url: String
            get() = _url.get()
            set(value) {
                _url.set(value)
                _url.disallowChanges()
            }

        var sha256: String
            get() = _sha256.get()
            set(value) {
                _sha256.set(value)
                _sha256.disallowChanges()
            }

        internal val waybill: WayBill
            get() = WayBill(url, sha256, name)

        override fun getName(): String = name
    }

    open class DownloadsExtension @Inject constructor(private val project: Project) {
        val downloads = project.objects.domainObjectContainer(DownloadHandler::class.java)

        fun all(): Provider<DirectoryProperty> {
            return project.tasks.named<Download>("download").map { task ->
                task.dest
            }
        }

        fun get(download: DownloadHandler): Provider<RegularFile> {
            return project.tasks.named<Download>("download").flatMap { task ->
                task.dest.map { it.file(downloads[download.name].name) }
            }
        }

        fun getUnpacked(download: DownloadHandler): Provider<Directory> {
            return project.tasks.named<Unpack>("unpack").flatMap { task ->
                task.dest.map { it.dir(downloads[download.name].name) }
            }
        }

        companion object {
            val Project.downloads: DownloadsExtension
                get() =
                    extensions.getByName<DownloadsExtension>("downloads")

            fun Project.downloads(action: Action<NamedDomainObjectContainer<DownloadHandler>>) {
                action.execute(extensions.getByName<DownloadsExtension>("downloads").downloads)
            }
        }
    }

    override fun apply(project: Project): Unit = project.run {
        val downloads = extensions.create<DownloadsExtension>("downloads")

        val generateWaybills by tasks.registering(GenerateWaybills::class) {
            waybills.set(downloads.downloads.map { it.waybill })
        }

        val download by tasks.registering(Download::class) {
            src.set(generateWaybills.flatMap { it.dest })
        }

        tasks.register<Unpack>("unpack") {
            src.set(download.flatMap { it.dest })
        }
    }
}

