package build

import build.util.makeTaskName
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayInputStream

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
                    project.providers.exec {
                        commandLine(
                            "anaconda",
                            "login",
                            "--username",
                            taskSpec.credentials.username,
                            "--password",
                            taskSpec.credentials.password
                        )

                        standardInput = ByteArrayInputStream("yes".toByteArray())
                    }.result.get()

                    project.providers.exec {
                        commandLine("anaconda", "upload", "-u", taskSpec.username, artifactPath.toString())
                    }.result.get()
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
