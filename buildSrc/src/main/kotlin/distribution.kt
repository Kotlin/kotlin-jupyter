import org.gradle.api.Project
import org.gradle.api.Task
import java.nio.file.Path

interface DistribOptions {
    val isOnProtectedBranch: Boolean
    val cleanInstallDirDistrib: Task

    val condaGroup: String
    val pyPiGroup: String
    val buildGroup: String

    val condaTaskSpecs: UploadTaskSpecs<CondaTaskSpec>
    val pyPiTaskSpecs: UploadTaskSpecs<PyPiTaskSpec>

    val distribKernelDir: String
    val distributionPath: Path

    val distribUtilsPath: Path
    val distribUtilRequirementsPath: Path
    val removeTypeHints: Boolean
    val typeHintsRemover: Path
}

interface ProjectWithDistribOptions: Project, DistribOptions

open class TaskSpec (
    var taskName: String = ""
)

interface DistributionPackageSettings {
    val dir: String
    val name: String
    val fileName: String
}

class UploadTaskSpecs <T : TaskSpec>(
        val packageSettings: DistributionPackageSettings,
        private val repoName: String,
        private val taskGroup: String,
        private val stable: T, private val dev: T
) {
    init {
        this.stable.taskName = taskName("Stable")
        this.dev.taskName = taskName("Dev")
    }

    private fun taskName(type: String) = repoName + "Upload" + type

    fun createTasks(project: ProjectWithDistribOptions, taskCreationAction: (T) -> Unit) {
        with(project) {
            if (isOnProtectedBranch) {
                taskCreationAction(stable)
            }
            taskCreationAction(dev)

            project.task(taskName("Protected")) {
                dependsOn(cleanInstallDirDistrib)
                group = taskGroup
                if (isOnProtectedBranch) {
                    dependsOn(dev.taskName)
                }
            }
        }
    }
}

class CondaCredentials (
    val username: String,
    val password: String
)

class CondaTaskSpec(
    val username: String,
    val credentials: CondaCredentials
) : TaskSpec()

class PyPiTaskSpec (
    val repoURL: String,
    val username: String,
    val password: String
) : TaskSpec()
