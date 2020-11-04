package org.jetbrains.kotlin.jupyter.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.jupyter.build.AllOptions
import org.jetbrains.kotlin.jupyter.build.CondaCredentials
import org.jetbrains.kotlin.jupyter.build.CondaTaskSpec
import org.jetbrains.kotlin.jupyter.build.DistributionPackageSettings
import org.jetbrains.kotlin.jupyter.build.PyPiTaskSpec
import org.jetbrains.kotlin.jupyter.build.UploadTaskSpecs
import org.jetbrains.kotlin.jupyter.build.detectVersion
import org.jetbrains.kotlin.jupyter.build.getFlag
import org.jetbrains.kotlin.jupyter.build.getSubDir
import org.jetbrains.kotlin.jupyter.build.stringPropOrEmpty
import java.nio.file.Path
import java.nio.file.Paths

fun Project.options(): AllOptions {
    val taskOptions: AllOptions by project.extra {
        val baseVersion: String by project

        object : AllOptions {
            override val packageName = "kotlin-jupyter-kernel"

            override val versionFileName = "VERSION"
            override val rootPath: Path = rootDir.toPath()

            override val isLocalBuild = getFlag("build.isLocal")

            override val artifactsDir: Path

            init {
                val artifactsPathStr = rootProject.findProperty("artifactsPath") as? String ?: "artifacts"
                artifactsDir = rootPath.resolve(artifactsPathStr)

                if (isLocalBuild) {
                    project.delete(artifactsDir)
                }

                project.version = detectVersion(baseVersion, artifactsDir, versionFileName)
                println("##teamcity[buildNumber '$version']")
            }

            override val readmePath: Path = rootPath.resolve("docs").resolve("README.md")

            private val installPath = rootProject.findProperty("installPath") as String?

            override val librariesPath = "libraries"
            override val librariesPropertiesPath: Path = rootPath.resolve(librariesPath).resolve(".properties")

            override val installPathLocal: Path = if (installPath != null) Paths.get(installPath)
            else Paths.get(System.getProperty("user.home").toString(), ".ipython", "kernels", "kotlin")

            override val resourcesDir = "resources"
            override val distribBuildPath: Path = rootPath.resolve("build").resolve("distrib-build")
            override val logosPath = getSubDir(rootPath, resourcesDir, "logos")
            override val nbExtensionPath = getSubDir(rootPath, resourcesDir, "notebook-extension")
            override val distributionPath: Path by extra(rootPath.resolve("distrib"))
            override val jarsPath = "jars"
            override val configDir = "config"

            // Straight slash is used 'cause it's universal across the platforms, and is used in jar_args config
            override val jarArgsFile = "$configDir/jar_args.json"
            override val runKernelPy = "run_kernel.py"
            override val kernelFile = "kernel.json"
            override val mainClassFQN = "org.jetbrains.kotlin.jupyter.IkotlinKt"
            override val installKernelTaskPrefix = "installKernel"
            override val cleanInstallDirTaskPrefix = "cleanInstallDir"
            override val copyLibrariesTaskPrefix = "copyLibraries"
            override val installLibsTaskPrefix = "installLibs"

            override val localGroup = "local install"
            override val distribGroup = "distrib"
            override val condaGroup = "conda"
            override val pyPiGroup = "pip"
            override val buildGroup = "build"

            private val debugPort = 1044
            override val debuggerConfig = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort"

            override val mainSourceSetDir = "main"
            override val runtimePropertiesFile = "runtime.properties"

            override val distribKernelDir = "kernel"
            override val runKernelDir = "run_kotlin_kernel"
            override val setupPy = "setup.py"

            override val copyRunKernelPy: Task
                get() = tasks.getByName("copyRunKernelPy")
            override val prepareDistributionDir: Task
                get() = tasks.getByName("prepareDistributionDir")
            override val cleanInstallDirDistrib: Task
                get() = tasks.getByName("cleanInstallDirDistrib")

            override val isOnProtectedBranch: Boolean
                get() = extra["isOnProtectedBranch"] as Boolean

            override val distribUtilsPath: Path = rootPath.resolve("distrib-util")
            override val distribUtilRequirementsPath: Path = distribUtilsPath.resolve("requirements-common.txt")
            override val distribUtilRequirementsHintsRemPath: Path =
                distribUtilsPath.resolve("requirements-hints-remover.txt")
            override val removeTypeHints = true
            override val typeHintsRemover: Path = distribUtilsPath.resolve("remove_type_hints.py")

            override val condaTaskSpecs by lazy {
                val condaUserStable = stringPropOrEmpty("condaUserStable")
                val condaPasswordStable = stringPropOrEmpty("condaPasswordStable")
                val condaUserDev = stringPropOrEmpty("condaUserDev")

                val condaPackageSettings = object : DistributionPackageSettings {
                    override val dir = "conda-package"
                    override val name = packageName
                    override val fileName by lazy { "$name-$version-py_0.tar.bz2" }
                }

                val condaCredentials = CondaCredentials(condaUserStable, condaPasswordStable)
                UploadTaskSpecs(
                    condaPackageSettings,
                    "conda",
                    condaGroup,
                    CondaTaskSpec(
                        condaUserStable,
                        condaCredentials
                    ),
                    CondaTaskSpec(
                        condaUserDev,
                        condaCredentials
                    )
                )
            }

            override val pyPiTaskSpecs by lazy {
                val stablePyPiUser = stringPropOrEmpty("stablePyPiUser")
                val stablePyPiPassword = stringPropOrEmpty("stablePyPiPassword")
                val devPyPiUser = stringPropOrEmpty("devPyPiUser")
                val devPyPiPassword = stringPropOrEmpty("devPyPiPassword")

                val pyPiPackageSettings = object : DistributionPackageSettings {
                    override val dir = "pip-package"
                    override val name = packageName.replace("-", "_")
                    override val fileName by lazy { "$name-$version-py3-none-any.whl" }
                }

                UploadTaskSpecs(
                    pyPiPackageSettings,
                    "pyPi",
                    pyPiGroup,
                    PyPiTaskSpec(
                        "https://upload.pypi.org/legacy/",
                        stablePyPiUser,
                        stablePyPiPassword
                    ),
                    PyPiTaskSpec(
                        "https://test.pypi.org/legacy/",
                        devPyPiUser,
                        devPyPiPassword
                    )
                )
            }
        }
    }

    return taskOptions
}
