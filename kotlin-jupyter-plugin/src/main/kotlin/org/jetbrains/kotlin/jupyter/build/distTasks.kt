package org.jetbrains.kotlin.jupyter.build

import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import java.io.ByteArrayInputStream
import java.nio.file.Path

private fun ProjectWithDistribOptions.removeTypeHintsIfNeeded(files: List<Path>) {
    if (!removeTypeHints)
        return

    files.forEach {
        val fileName = it.toAbsolutePath().toString()
        exec {
            commandLine("python", typeHintsRemover, fileName, fileName)
        }
    }
}

fun ProjectWithOptions.prepareDistributionTasks() {
    tasks.register<PipInstallReq>("installCommonRequirements") {
        group = distribGroup
        requirementsFile = distribUtilRequirementsPath
    }

    tasks.register<PipInstallReq>("installHintRemoverRequirements") {
        group = distribGroup
        requirementsFile = distribUtilRequirementsHintsRemPath
    }

    tasks.register<Copy>("copyDistribFiles") {
        group = distribGroup
        dependsOn("cleanInstallDirDistrib")
        if (removeTypeHints) {
            dependsOn("installHintRemoverRequirements")
        }
        from(distributionPath)
        from(readmePath)
        into(distribBuildPath)
        exclude(".idea/**")

        val pythonFiles = mutableListOf<Path>()
        eachFile {
            val absPath = distribBuildPath.resolve(this.path).toAbsolutePath()
            if (this.path.endsWith(".py"))
                pythonFiles.add(absPath)
        }

        doLast {
            removeTypeHintsIfNeeded(pythonFiles)
        }
    }

    task("prepareDistributionDir") {
        group = distribGroup
        dependsOn("cleanInstallDirDistrib", "copyDistribFiles")
        doLast {
            val versionFilePath = distribBuildPath.resolve(versionFileName)
            versionFilePath.toFile().writeText(version as String)
            project.copy {
                from(versionFilePath)
                into(artifactsDir)
            }
        }
    }
}

fun ProjectWithOptions.prepareCondaTasks() {
    with(condaTaskSpecs) {
        tasks.register<Exec>("condaPackage") {
            group = condaGroup
            dependsOn("cleanInstallDirDistrib", "preparePackage")
            commandLine("conda-build", "conda", "--output-folder", packageSettings.dir)
            workingDir(distribBuildPath)
            doLast {
                copy {
                    from(distribBuildPath.resolve(packageSettings.dir).resolve("noarch").resolve(packageSettings.fileName))
                    into(artifactsDir)
                }
            }
        }

        condaTaskSpecs.createTasks(this@prepareCondaTasks) { taskSpec ->
            task(taskSpec.taskName) {
                group = condaGroup
                val artifactPath = artifactsDir.resolve(packageSettings.fileName)

                if (!artifactPath.toFile().exists()) {
                    dependsOn("cleanInstallDirDistrib", "condaPackage")
                }

                doLast {
                    exec {
                        commandLine(
                            "anaconda",
                            "login",
                            "--username",
                            taskSpec.credentials.username,
                            "--password",
                            taskSpec.credentials.password
                        )

                        standardInput = ByteArrayInputStream("yes".toByteArray())
                    }

                    exec {
                        commandLine("anaconda", "upload", "-u", taskSpec.username, artifactPath.toString())
                    }
                }
            }
        }
    }
}

fun ProjectWithOptions.preparePyPiTasks() {
    with(pyPiTaskSpecs) {
        tasks.register<Exec>("pyPiPackage") {
            group = pyPiGroup

            dependsOn("preparePackage")
            if (isLocalBuild) {
                dependsOn("installCommonRequirements")
            }

            commandLine(
                "python",
                setupPy,
                "bdist_wheel",
                "--dist-dir",
                packageSettings.dir
            )
            workingDir(distribBuildPath)

            doLast {
                copy {
                    from(distribBuildPath.resolve(packageSettings.dir).resolve(packageSettings.fileName))
                    into(artifactsDir)
                }
            }
        }

        pyPiTaskSpecs.createTasks(this@preparePyPiTasks) { taskSpec ->
            tasks.register<Exec>(taskSpec.taskName) {
                group = pyPiGroup
                workingDir(artifactsDir)
                val artifactPath = artifactsDir.resolve(packageSettings.fileName)

                if (isLocalBuild) {
                    dependsOn("installCommonRequirements")
                }
                if (!artifactPath.toFile().exists()) {
                    dependsOn("pyPiPackage")
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

fun ProjectWithOptions.prepareAggregateUploadTasks() {
    val infixToSpec = mapOf<String, (UploadTaskSpecs<*>) -> TaskSpec>(
        "Dev" to { it.dev },
        "Stable" to { it.stable }
    )

    infixToSpec.forEach { (infix, taskSpecGetter) ->
        val tasksList = mutableListOf<String>()
        listOf(condaTaskSpecs, pyPiTaskSpecs).forEach { taskSpec ->
            tasksList.add(taskSpecGetter(taskSpec).taskName)
        }

        tasksList.add("bintrayUpload")

        tasks.register("aggregate${infix}Upload") {
            group = distribGroup
            dependsOn(tasksList)
        }
    }
}
