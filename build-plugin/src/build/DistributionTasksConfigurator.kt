package build

import build.util.defaultVersionCatalog
import build.util.devKotlin
import build.util.stableKotlin
import build.util.gradleKotlin
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

        files.forEach {
            val fileName = it.absolutePath
            project.exec {
                commandLine("python", settings.typeHintsRemover, fileName, fileName)
            }
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
                val projectVersions = project.defaultVersionCatalog.versions
                versionsCompatFilePath.writeText(
                    """
                        pythonPackageVersion=${settings.pyPackageVersion}
                        mavenVersion=${settings.mavenVersion}
                        kotlinLibrariesVersion=${projectVersions.devKotlin}
                        kotlinCompilerVersion=${projectVersions.stableKotlin}
                        kotlinGradleLibrariesVersion=${projectVersions.gradleKotlin}
                        kotlinLanguageLevel=${settings.kotlinLanguageLevel}
                    """.trimIndent()
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
