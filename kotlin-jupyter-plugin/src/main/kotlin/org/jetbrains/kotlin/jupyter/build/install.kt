package org.jetbrains.kotlin.jupyter.build

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import java.nio.file.Path

interface BuildOptions {
    val isLocalBuild: Boolean
    val mainSourceSetDir: String
    val runtimePropertiesFile: String
    val resourcesDir: String
    val versionFileName: String
    val rootPath: Path

    val artifactsDir: Path
    val readmePath: Path
}

interface ProjectWithBuildOptions: Project, BuildOptions

interface InstallOptions {
    val installPathLocal: Path
    val distribBuildPath: Path
    val logosPath: Path
    val nbExtensionPath: Path

    val librariesPath: String
    val librariesPropertiesPath: Path
    val jarsPath: String
    val configDir: String
    val jarArgsFile: String
    val runKernelDir: String
    val runKernelPy: String
    val setupPy: String
    val kernelFile: String
    val mainClassFQN: String

    val installKernelTaskPrefix: String
    val cleanInstallDirTaskPrefix: String
    val copyLibrariesTaskPrefix: String
    val installLibsTaskPrefix: String

    val localGroup: String
    val distribGroup: String

    val debuggerConfig: String

    val copyRunKernelPy: Task
    val prepareDistributionDir: Task
}

interface ProjectWithInstallOptions: Project, InstallOptions

fun ProjectWithInstallOptions.createCleanTasks() {
    listOf(true, false).forEach { local ->
        val dir = if(local) installPathLocal else distribBuildPath
        task(makeTaskName(cleanInstallDirTaskPrefix, local)) {
            group = if(local) localGroup else distribGroup
            doLast {
                if(!dir.deleteDir()) {
                    throw Exception("Cannot delete $dir")
                }
            }
        }
    }
}


fun ProjectWithInstallOptions.createInstallTasks(local: Boolean, specPath: Path, mainInstallPath: Path) {
    val groupName = if(local) localGroup else distribGroup
    val cleanDirTask = tasks.getByName(makeTaskName(cleanInstallDirTaskPrefix, local))
    val shadowJar = tasks.getByName("shadowJar")

    tasks.register<Copy>(makeTaskName(copyLibrariesTaskPrefix, local)) {
        dependsOn(cleanDirTask)
        group = groupName
        from(librariesPath)
        into(mainInstallPath.resolve(librariesPath))
    }

    tasks.register<Copy>(makeTaskName(installLibsTaskPrefix, local)) {
        dependsOn(cleanDirTask)
        group = groupName
        from(configurations["deploy"])
        into(mainInstallPath.resolve(jarsPath))
    }

    tasks.register<Copy>(makeTaskName(installKernelTaskPrefix, local)) {
        dependsOn(cleanDirTask, shadowJar)
        group = groupName
        from (shadowJar.outputs)
        into (mainInstallPath.resolve(jarsPath))
    }

    listOf(true, false).forEach { debug ->
        val specTaskName = createTaskForSpecs(debug, local, groupName, cleanDirTask, shadowJar, specPath, mainInstallPath)
        createMainInstallTask(debug, local, groupName, specTaskName)
    }
}


fun ProjectWithInstallOptions.createTaskForSpecs(debug: Boolean, local: Boolean, group: String, cleanDir: Task, shadowJar: Task, specPath: Path, mainInstallPath: Path): String {
    val taskName = makeTaskName(if(debug) "createDebugSpecs" else "createSpecs", local)
    tasks.register(taskName) {
        this.group = group
        dependsOn (cleanDir, shadowJar)
        doLast {
            val kernelFile = files(shadowJar).singleFile

            val libsCp = files(configurations["deploy"]).files.map { it.name }

            makeDirs(mainInstallPath.resolve(jarsPath))
            makeDirs(mainInstallPath.resolve(configDir))
            makeDirs(specPath)

            makeJarArgs(mainInstallPath, kernelFile.name, mainClassFQN, libsCp, if (debug) debuggerConfig else "")
            makeKernelSpec(specPath, local)
        }
    }
    return taskName
}

fun ProjectWithInstallOptions.createMainInstallTask(debug: Boolean, local: Boolean, group: String, specsTaskName: String) {
    val taskNamePrefix = if(local)  "install" else "prepare"
    val taskNameMiddle = if(debug) "Debug" else ""
    val taskNameSuffix = if(local) "" else "Package"
    val taskName = "$taskNamePrefix$taskNameMiddle$taskNameSuffix"

    val dependencies = listOf(
        makeTaskName(cleanInstallDirTaskPrefix, local),
        if(local) copyRunKernelPy else prepareDistributionDir,
        makeTaskName(installKernelTaskPrefix, local),
        makeTaskName(installLibsTaskPrefix, local),
        specsTaskName,
        makeTaskName(copyLibrariesTaskPrefix, local)
    )

    task(taskName) {
        this.group = group
        dependsOn(dependencies)
    }
}

fun ProjectWithInstallOptions.makeKernelSpec(installPath: Path, localInstall: Boolean) {
    val argv = if(localInstall) {
        listOf("python",
                installPath.resolve(runKernelPy).toString(),
                "{connection_file}",
                installPath.resolve(jarArgsFile).toString(),
                installPath.toString())
    } else {
        listOf("python", "-m", "run_kotlin_kernel", "{connection_file}")
    }

    writeJson(mapOf(
            "display_name" to "Kotlin",
            "language" to "kotlin",
            "interrupt_mode" to "message",
            "argv" to argv
    ), installPath.resolve(kernelFile))

    project.copy {
        from(nbExtensionPath, logosPath)
        into(installPath)
    }
}

fun ProjectWithInstallOptions.makeJarArgs(
        installPath: Path,
        kernelJarPath: String,
        mainClassFQN: String,
        classPath: List<String>,
        debuggerConfig: String = ""
) {
    writeJson(mapOf(
            "mainJar" to kernelJarPath,
            "mainClass" to mainClassFQN,
            "classPath" to classPath,
            "debuggerConfig" to debuggerConfig
    ), installPath.resolve(jarArgsFile))
}
