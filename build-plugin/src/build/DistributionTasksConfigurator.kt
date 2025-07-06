package build

import build.util.PipInstallReq
import build.util.makeTaskName
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register
import java.io.File

class DistributionTasksConfigurator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    private fun removeTypeHintsIfNeeded(files: List<File>) {
        if (!settings.removeTypeHints)
            return

        for (file in files) {
            val fileName = file.absolutePath
            project.providers.exec {
                commandLine("python", settings.typeHintsRemover, fileName, fileName)
            }.result.get()
        }
    }

    fun registerTasks() {
        project.tasks.register<PipInstallReq>(INSTALL_COMMON_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = settings.distribUtilRequirementsFile
        }

        project.tasks.register<PipInstallReq>(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = settings.distribUtilRequirementsHintsRemoverFile
        }

        project.tasks.register<Copy>(COPY_DISTRIB_FILES_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false))
            if (settings.removeTypeHints) {
                dependsOn(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK)
            }
            from(settings.distributionDir)
            from(settings.readmeFile)
            into(settings.distribBuildDir)
            exclude(".idea/**", "venv/**")

            val pythonFiles = mutableListOf<File>()
            eachFile {
                val absPath = settings.distribBuildDir.resolve(this.path).absoluteFile
                if (this.path.endsWith(".py"))
                    pythonFiles.add(absPath)
            }

            doLast {
                removeTypeHintsIfNeeded(pythonFiles)
            }
        }

        project.tasks.register(PREPARE_DISTRIBUTION_DIR_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false), COPY_DISTRIB_FILES_TASK)
            doLast {
                val versionFilePath = settings.distribBuildDir.resolve(settings.versionFileName)
                versionFilePath.writeText(settings.pyPackageVersion)

                val versionsCompatFilePath = settings.distribBuildDir.resolve(settings.versionsCompatFileName)
                versionsCompatFilePath.writeText(
                    settings.compatibilityAttributes.joinToString("\n") { attr ->
                        "${attr.tcPropertyName}=${attr.value}"
                    }
                )
                project.copy {
                    from(versionFilePath, versionsCompatFilePath)
                    into(settings.artifactsDir)
                }

                settings.distribBuildDir.resolve("REPO_URL").writeText(settings.projectRepoUrl)
            }
        }
    }
}
