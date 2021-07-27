package build

import build.util.makeDirs
import build.util.makeTaskName
import build.util.writeJson
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.io.File

class InstallTasksConfigurator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    fun createLocalInstallTasks() {
        project.tasks.register<Copy>(COPY_RUN_KERNEL_PY_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, true))
            from(settings.runKernelDir.resolve(settings.runKernelPy))
            from(settings.distributionDir.resolve(settings.kotlinKernelModule)) {
                into(settings.kotlinKernelModule)
            }
            into(settings.localInstallDir)
        }

        project.tasks.register<Copy>(COPY_NB_EXTENSION_TASK) {
            group = LOCAL_INSTALL_GROUP
            from(settings.nbExtensionDir)
            into(settings.localInstallDir)
        }

        createInstallTasks(true, settings.localInstallDir, settings.localInstallDir)

        project.task(UNINSTALL_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false))
        }
    }

    fun createInstallTasks(local: Boolean, specPath: File, mainInstallPath: File) {
        val groupName = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
        val cleanDirTask = project.tasks.getByName(makeTaskName(settings.cleanInstallDirTaskPrefix, local))
        val shadowJar = project.tasks.getByName(SHADOW_JAR_TASK)
        val updateLibrariesTask = project.tasks.named(UPDATE_LIBRARIES_TASK_NAME)

        project.tasks.register<Copy>(makeTaskName(settings.copyLibrariesTaskPrefix, local)) {
            dependsOn(cleanDirTask, updateLibrariesTask)
            group = groupName
            from(settings.librariesDir)
            into(mainInstallPath.resolve(settings.librariesDir))
        }

        project.tasks.register<Copy>(makeTaskName(settings.installLibsTaskPrefix, local)) {
            dependsOn(cleanDirTask)
            group = groupName
            from(project.configurations["deploy"])
            into(mainInstallPath.resolve(settings.jarsPath))
        }

        project.tasks.register<Copy>(makeTaskName(settings.installKernelTaskPrefix, local)) {
            dependsOn(cleanDirTask, shadowJar)
            group = groupName
            from(shadowJar.outputs)
            into(mainInstallPath.resolve(settings.jarsPath))
        }

        listOf(true, false).forEach { debug ->
            val specTaskName = createTaskForSpecs(debug, local, groupName, cleanDirTask, shadowJar, specPath, mainInstallPath)
            createMainInstallTask(debug, local, groupName, specTaskName)
        }
    }

    private fun createTaskForSpecs(debug: Boolean, local: Boolean, group: String, cleanDir: Task, shadowJar: Task, specPath: File, mainInstallPath: File): String {
        val taskName = makeTaskName(if (debug) "createDebugSpecs" else "createSpecs", local)
        project.tasks.register(taskName) {
            this.group = group
            dependsOn(cleanDir, shadowJar)
            doLast {
                val kernelFile = project.files(shadowJar).singleFile

                val libsCp = project.files(project.configurations["deploy"]).files.map { it.name }

                makeDirs(mainInstallPath.resolve(settings.jarsPath))
                makeDirs(mainInstallPath.resolve(settings.configDir))
                makeDirs(specPath)

                writeJson(
                    mapOf(
                        "mainJar" to kernelFile.name,
                        "mainClass" to settings.mainClassFQN,
                        "classPath" to libsCp,
                        "debuggerConfig" to if (debug) settings.debuggerConfig else ""
                    ),
                    mainInstallPath.resolve(settings.jarArgsFile)
                )
                makeKernelSpec(specPath, local)
            }
        }
        return taskName
    }

    private fun createMainInstallTask(debug: Boolean, local: Boolean, group: String, specsTaskName: String) {
        val taskNamePrefix = if (local) "install" else "prepare"
        val taskNameMiddle = if (debug) "Debug" else ""
        val taskNameSuffix = if (local) "" else "Package"
        val taskName = "$taskNamePrefix$taskNameMiddle$taskNameSuffix"

        val dependencies = listOf(
            makeTaskName(settings.cleanInstallDirTaskPrefix, local),
            if (local) project.tasks.getByName(COPY_RUN_KERNEL_PY_TASK) else project.tasks.getByName(PREPARE_DISTRIBUTION_DIR_TASK),
            makeTaskName(settings.installKernelTaskPrefix, local),
            makeTaskName(settings.installLibsTaskPrefix, local),
            specsTaskName,
            makeTaskName(settings.copyLibrariesTaskPrefix, local)
        )

        project.task(taskName) {
            this.group = group
            dependsOn(dependencies)
        }
    }

    private fun makeKernelSpec(installPath: File, localInstall: Boolean) {
        val argv = if (localInstall) {
            listOf(
                "python",
                installPath.resolve(settings.runKernelPy).toString(),
                "{connection_file}",
                installPath.resolve(settings.jarArgsFile).toString(),
                installPath.toString()
            )
        } else {
            listOf("python", "-m", "run_kotlin_kernel", "{connection_file}")
        }

        writeJson(
            mapOf(
                "display_name" to "Kotlin",
                "language" to "kotlin",
                "interrupt_mode" to "message",
                "argv" to argv
            ),
            installPath.resolve(settings.kernelFile)
        )

        project.copy {
            from(settings.nbExtensionDir, settings.logosDir)
            into(installPath)
        }
    }
}
