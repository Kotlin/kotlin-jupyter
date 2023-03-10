package build

import build.util.makeDirs
import build.util.makeTaskName
import build.util.writeJson
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.io.File

class InstallTasksConfigurator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    fun registerLocalInstallTasks() {
        project.tasks.register<Copy>(COPY_RUN_KERNEL_PY_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, true))
            moduleFromDistributionDir(settings.runKotlinKernelModule)
            moduleFromDistributionDir(settings.kotlinKernelModule)
            into(settings.localInstallDir)
            from(settings.distributionDir.resolve(settings.localRunPy))
        }

        project.tasks.register<Copy>(COPY_NB_EXTENSION_TASK) {
            group = LOCAL_INSTALL_GROUP
            from(settings.nbExtensionDir)
            into(settings.localInstallDir)
        }

        registerInstallTasks(true, settings.localInstallDir, settings.localInstallDir)

        project.tasks.register(UNINSTALL_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(settings.cleanInstallDirTaskPrefix, false))
        }
    }

    fun registerInstallTasks(local: Boolean, specPath: File, mainInstallPath: File) {
        val groupName = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
        val cleanDirTask = project.tasks.named(makeTaskName(settings.cleanInstallDirTaskPrefix, local))
        val shadowJar = project.tasks.named(SHADOW_JAR_TASK)
        val jarTask = project.tasks.named(JAR_TASK)
        val updateLibrariesTask = project.tasks.named(UPDATE_LIBRARIES_TASK)

        project.tasks.register<Copy>(makeTaskName(settings.copyLibrariesTaskPrefix, local)) {
            dependsOn(cleanDirTask, updateLibrariesTask)
            group = groupName
            from(settings.librariesDir)
            into(mainInstallPath.librariesDir)
        }

        project.tasks.register<Copy>(makeTaskName(settings.installLibsTaskPrefix, local)) {
            dependsOn(cleanDirTask)
            group = groupName
            from(project.configurations["deploy"])
            into(mainInstallPath.localJarsDir)
        }

        project.tasks.register<Copy>(makeTaskName(settings.installKernelTaskPrefix, local)) {
            dependsOn(cleanDirTask, shadowJar)
            mustRunAfter(jarTask)
            group = groupName
            from(shadowJar.get().outputs)
            into(mainInstallPath.localJarsDir)
        }

        listOf(true, false).forEach { debug ->
            val specTaskName = registerTaskForSpecs(debug, local, groupName, cleanDirTask, shadowJar, specPath, mainInstallPath)
            registerMainInstallTask(debug, local, groupName, specTaskName)
        }
    }

    private fun registerTaskForSpecs(
        debug: Boolean,
        local: Boolean,
        group: String,
        cleanDir: TaskProvider<*>,
        shadowJar: TaskProvider<*>,
        specPath: File,
        mainInstallPath: File
    ): String {
        val taskName = makeTaskName("create${debugStr(debug)}Specs", local)
        project.tasks.register(taskName) {
            this.group = group
            dependsOn(cleanDir, shadowJar)
            doLast {
                val kernelFile = project.files(shadowJar).singleFile

                val libsCp = project.files(project.configurations["deploy"]).files.map { it.name }

                makeDirs(mainInstallPath.localJarsDir)
                makeDirs(mainInstallPath.configDir)
                makeDirs(specPath)

                writeJson(
                    mapOf(
                        "mainJar" to kernelFile.name,
                        "mainClass" to settings.mainClassFQN,
                        "classPath" to libsCp,
                        "debuggerPort" to if (debug) settings.debuggerPort else ""
                    ),
                    mainInstallPath.jarArgsFile
                )
                makeKernelSpec(specPath, local)
            }
        }
        return taskName
    }

    private fun registerMainInstallTask(debug: Boolean, local: Boolean, group: String, specsTaskName: String) {
        project.tasks.register(mainInstallTaskName(debug, local)) {
            this.group = group
            dependsOn(
                makeTaskName(settings.cleanInstallDirTaskPrefix, local),
                if (local) COPY_RUN_KERNEL_PY_TASK else PREPARE_DISTRIBUTION_DIR_TASK,
                makeTaskName(settings.installKernelTaskPrefix, local),
                makeTaskName(settings.installLibsTaskPrefix, local),
                specsTaskName,
                makeTaskName(settings.copyLibrariesTaskPrefix, local),
            )
        }
    }

    private fun makeKernelSpec(installPath: File, localInstall: Boolean) {
        val firstArg = if (localInstall) installPath.resolve(settings.localRunPy).toString() else "-m"
        fun execPythonArgs(vararg args: String) = listOf("python", firstArg, *args)

        val argv = execPythonArgs(settings.runKotlinKernelModule, "{connection_file}")
        val jarsPathDetectorArgv = execPythonArgs(settings.kotlinKernelModule, "detect-jars-location")

        writeJson(
            mapOf(
                "display_name" to "Kotlin",
                "language" to "kotlin",
                "interrupt_mode" to "message",
                "argv" to argv,
                "metadata" to mapOf(
                    "jar_path_detect_command" to jarsPathDetectorArgv,
                ),
            ),
            installPath.resolve(settings.kernelFile)
        )

        project.copy {
            from(settings.nbExtensionDir, settings.logosDir)
            into(installPath)
        }
    }

    private val File.localJarsDir get() = resolve(settings.runKotlinKernelModule).resolve(settings.jarsPath)
    private val File.librariesDir get() = resolve(settings.runKotlinKernelModule).resolve(settings.librariesDir)
    private val File.configDir get() = resolve(settings.runKotlinKernelModule).resolve(settings.configDir)
    private val File.jarArgsFile get() = resolve(settings.runKotlinKernelModule).resolve(settings.jarArgsFile)

    private fun Copy.moduleFromDistributionDir(moduleName: String) {
        from(settings.distributionDir.resolve(moduleName)) {
            into(moduleName)
        }
    }
}
