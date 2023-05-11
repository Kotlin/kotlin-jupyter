package build

import build.util.BUILD_LIBRARIES
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.property
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

abstract class CreateResourcesTask : DefaultTask() {
    private val resourceFileCounter = AtomicInteger()
    private val resources: MutableMap<String, String> = mutableMapOf()
    private var librariesDir: File? = null
    private var jarPath: String? = null

    @get:OutputDirectory
    val outputResourceDir: Property<File> = project.objects.property<File>().apply {
        set(project.provider { project.buildDir.resolve(this@CreateResourcesTask.name + "Resources") })
    }

    @TaskAction
    fun createResources() {
        val dir = outputResourceDir.get()
        dir.deleteRecursively()

        librariesDir?.let { libDir ->
            val libsList = libDir.list { _, fileName ->
                fileName.endsWith(".json")
            }?.toList().orEmpty()
            libsList.forEach {
                addResource("$jarPath/$it", libDir.resolve(it).readText())
            }
            val optionsFileName = BUILD_LIBRARIES.optionsFileName()
            addResource("$jarPath/$optionsFileName", libDir.resolve(optionsFileName).readText())
            addResource("$jarPath/libraries.list", libsList.joinToString("\n"))
        }

        resources.forEach { (subPath, text) ->
            val file = dir.resolve(subPath)
            file.parentFile.mkdirs()
            file.writeText(text)
        }
    }

    private fun addResource(subPath: String, text: String) {
        resources[subPath] = text
    }

    private fun setInputProperties(properties: Map<String, String>) {
        val number = resourceFileCounter.getAndIncrement()
        properties.forEach { (name, value) ->
            inputs.property("$name$number", value)
        }
    }

    fun addSingleValueFile(subPath: String, value: String) {
        addResource(subPath, value)
        setInputProperties(mapOf("value" to value))
    }

    fun addPropertiesFile(subPath: String, values: Map<String, String>) {
        inputs.property("__filename__", subPath)
        addResource(subPath, values.entries.joinToString("") { "${it.key}=${it.value}\n" })
        setInputProperties(values)
    }

    fun addPropertiesFile(subPath: String, values: List<Pair<String, String>>) {
        addPropertiesFile(subPath, values.associate { it })
    }

    fun addLibrariesFromDir(dir: File, jarPath: String = "jupyterLibraries") {
        this.librariesDir = dir
        this.jarPath = jarPath
        outputs.upToDateWhen { false }
    }

    private fun setupDependencies(resourceTask: Copy) {
        resourceTask.apply {
            dependsOn(this@CreateResourcesTask)
            from(outputResourceDir)
        }
    }

    companion object {
        fun register(
            project: Project,
            name: String,
            resourceTaskProvider: TaskProvider<out Copy>,
            action: Action<CreateResourcesTask>,
        ): TaskProvider<CreateResourcesTask> {
            val task = project.tasks.register(name, CreateResourcesTask::class.java, action)
            resourceTaskProvider.configure {
                task.get().setupDependencies(this)
            }
            return task
        }
    }
}
