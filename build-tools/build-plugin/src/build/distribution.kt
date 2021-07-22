package build

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.InputFile
import org.gradle.kotlin.dsl.getByType
import java.nio.file.Path

open class TaskSpec(
    var taskName: String = ""
)

class DistributionPackageSettings(val dir: String, val fileName: String)

class UploadTaskSpecs <T : TaskSpec>(
    val packageSettings: DistributionPackageSettings,
    private val repoName: String,
    private val taskGroup: String,
    val stable: T,
    val dev: T
) {
    init {
        this.stable.taskName = taskName("Stable")
        this.dev.taskName = taskName("Dev")
    }

    private fun taskName(type: String) = repoName + "Upload" + type

    fun createTasks(project: Project, taskCreationAction: (T) -> Unit) {
        val opts = project.extensions.getByType<KernelBuildExtension>()
        if (opts.isOnProtectedBranch) {
            taskCreationAction(stable)
        }
        taskCreationAction(dev)

        project.task(taskName("Protected")) {
            dependsOn(opts.cleanInstallDirDistrib)
            group = taskGroup
            if (opts.isOnProtectedBranch) {
                dependsOn(dev.taskName)
            }
        }
    }
}

class CondaCredentials(
    val username: String,
    val password: String
)

class CondaTaskSpec(
    val username: String,
    val credentials: CondaCredentials
) : TaskSpec()

class PyPiTaskSpec(
    val repoURL: String,
    val username: String,
    val password: String
) : TaskSpec()

abstract class PipInstallReq : Exec() {
    @get:InputFile
    var requirementsFile: Path? = null
        set(value) {
            commandLine("python", "-m", "pip", "install", "-r", value)
            field = value
        }
}
