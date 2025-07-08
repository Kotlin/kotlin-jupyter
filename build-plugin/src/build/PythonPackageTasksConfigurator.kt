package build

import build.util.makeTaskName
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

class PythonPackageTasksConfigurator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    fun registerTasks() {
        prepareCondaTasks()
        preparePyPiTasks()
    }

    private fun prepareCondaTasks() {
        val specs = settings.condaTaskSpecs
        val packageSettings = specs.packageSettings
        project.tasks.register<Exec>(CONDA_PACKAGE_TASK) {
            group = CONDA_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false), PREPARE_PACKAGE_TASK)
            commandLine("conda-build", "conda", "--output-folder", packageSettings.dir)
            workingDir(settings.distribBuildDir)
            doLast {
                project.copy {
                    from(settings.distribBuildDir.resolve(packageSettings.dir).resolve("noarch").resolve(packageSettings.fileName))
                    into(settings.artifactsDir)
                }
            }
        }

        project.tasks.named(PUBLISH_LOCAL_TASK) {
            dependsOn(CONDA_PACKAGE_TASK)
        }

        specs.registerTasks(settings) { taskSpec ->
            project.tasks.register(taskSpec.taskName) {
                group = CONDA_GROUP
                val artifactPath = settings.artifactsDir.resolve(packageSettings.fileName)

                if (!artifactPath.exists()) {
                    dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false), CONDA_PACKAGE_TASK)
                }

                doLast {
                    val execTask = project.providers.exec {
                        commandLine(
                            settings.anacondaUploadScript.absolutePath,
                            taskSpec.credentials.username,
                            taskSpec.credentials.password,
                            artifactPath.absolutePath.toString(),
                            taskSpec.username,
                        )
                        isIgnoreExitValue = true
                    }
                    if (execTask.result.get().exitValue != 0) {
                        val standardOutput = execTask.standardOutput.asText.get()
                        throw RuntimeException("Unable to publish conda package:\n$standardOutput")
                    }
                }
            }
        }
    }

    private fun preparePyPiTasks() {
        val specs = settings.pyPiTaskSpecs
        val packageSettings = specs.packageSettings
        project.tasks.register<Exec>(PYPI_PACKAGE_TASK) {
            group = PYPI_GROUP

            dependsOn(PREPARE_PACKAGE_TASK)
            if (settings.isLocalBuild) {
                dependsOn(INSTALL_COMMON_REQUIREMENTS_TASK)
            }

            commandLine(
                "python",
                settings.setupPy,
                "bdist_wheel",
                "--dist-dir",
                packageSettings.dir
            )
            workingDir(settings.distribBuildDir)

            doLast {
                project.copy {
                    from(settings.distribBuildDir.resolve(packageSettings.dir).resolve(packageSettings.fileName))
                    into(settings.artifactsDir)
                }
            }
        }

        project.tasks.named(PUBLISH_LOCAL_TASK) {
            dependsOn(PYPI_PACKAGE_TASK)
        }

        specs.registerTasks(settings) { taskSpec ->
            project.tasks.register<Exec>(taskSpec.taskName) {
                group = PYPI_GROUP
                workingDir(settings.artifactsDir)
                val artifactPath = settings.artifactsDir.resolve(packageSettings.fileName)

                if (settings.isLocalBuild) {
                    dependsOn(INSTALL_COMMON_REQUIREMENTS_TASK)
                }
                if (!artifactPath.exists()) {
                    dependsOn(PYPI_PACKAGE_TASK)
                }

                commandLine(
                    "twine", "upload",
                    "-u", taskSpec.username,
                    "-p", taskSpec.password,
                    "--repository-url", taskSpec.repoURL,
                    packageSettings.fileName
                )
            }
        }
    }
}
