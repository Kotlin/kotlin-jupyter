package build

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import java.nio.file.Path
import java.nio.file.Paths

open class KernelBuildExtension(
    private val project: Project
) {
    var packageName = "kotlin-jupyter-kernel"

    val versionFileName = "VERSION"
    val rootPath: Path = project.rootDir.toPath()

    val isLocalBuild = project.getFlag("build.isLocal")

    val artifactsDir: Path = run {
        val artifactsPathStr = project.rootProject.findProperty("artifactsPath") as? String ?: "artifacts"
        val artifactsDir = rootPath.resolve(artifactsPathStr)

        if (isLocalBuild) {
            project.delete(artifactsDir)
        }
        return@run artifactsDir
    }

    val localPublicationsRepo: Path = artifactsDir.resolve("maven")

    val baseVersion = project.prop<String>("baseVersion")

    val pythonVersion: String = project.detectVersion(baseVersion, artifactsDir, versionFileName)

    val mavenVersion = pythonVersion.toMavenVersion()

    val readmePath: Path = rootPath.resolve("docs").resolve("README.md")

    private val installPath = project.prop<String?>("installPath")

    val librariesPath = "libraries"
    val librariesPropertiesPath: Path = rootPath.resolve(librariesPath).resolve(".properties")

    val installPathLocal: Path = if (installPath != null) Paths.get(installPath)
    else Paths.get(System.getProperty("user.home").toString(), ".ipython", "kernels", "kotlin")

    val resourcesDir = "resources"
    val distribBuildPath: Path = rootPath.resolve("build").resolve("distrib-build")
    val logosPath = getSubDir(rootPath, resourcesDir, "logos")
    val nbExtensionPath = getSubDir(rootPath, resourcesDir, "notebook-extension")
    val distributionPath: Path by project.extra(rootPath.resolve("distrib"))
    val jarsPath = "jars"
    val configDir = "config"

    // Straight slash is used 'cause it's universal across the platforms, and is used in jar_args config
    val jarArgsFile = "$configDir/jar_args.json"
    val runKernelPy = "run_kernel.py"
    val kotlinKernelModule = "kotlin_kernel"
    val kernelFile = "kernel.json"
    val mainClassFQN = "org.jetbrains.kotlinx.jupyter.IkotlinKt"
    val installKernelTaskPrefix = "installKernel"
    val cleanInstallDirTaskPrefix = "cleanInstallDir"
    val copyLibrariesTaskPrefix = "copyLibraries"
    val installLibsTaskPrefix = "installLibs"

    private val debugPort = 1044
    val debuggerConfig = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort"

    val mainSourceSetDir = "main"
    val runtimePropertiesFile = "runtime.properties"

    val distribKernelDir = "kernel"
    val runKernelDir = "run_kotlin_kernel"
    val setupPy = "setup.py"

    val copyRunKernelPy: Task
        get() = project.tasks.getByName("copyRunKernelPy")
    val prepareDistributionDir: Task
        get() = project.tasks.getByName("prepareDistributionDir")
    val cleanInstallDirDistrib: Task
        get() = project.tasks.getByName("cleanInstallDirDistrib")

    val isOnProtectedBranch: Boolean
        get() = project.extra["isOnProtectedBranch"] as Boolean

    val distribUtilsPath: Path = rootPath.resolve("distrib-util")
    val distribUtilRequirementsPath: Path = distribUtilsPath.resolve("requirements-common.txt")
    val distribUtilRequirementsHintsRemPath: Path =
        distribUtilsPath.resolve("requirements-hints-remover.txt")
    val removeTypeHints = true
    val typeHintsRemover: Path = distribUtilsPath.resolve("remove_type_hints.py")

    val condaTaskSpecs by lazy {
        val condaUserStable = project.stringPropOrEmpty("condaUserStable")
        val condaPasswordStable = project.stringPropOrEmpty("condaPasswordStable")
        val condaUserDev = project.stringPropOrEmpty("condaUserDev")

        val condaCredentials = CondaCredentials(condaUserStable, condaPasswordStable)
        UploadTaskSpecs(
            DistributionPackageSettings(
                "conda-package",
                "$packageName-$pythonVersion-py_0.tar.bz2"
            ),
            "conda",
            CONDA_GROUP,
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

    val pyPiTaskSpecs by lazy {
        val stablePyPiUser = project.stringPropOrEmpty("stablePyPiUser")
        val stablePyPiPassword = project.stringPropOrEmpty("stablePyPiPassword")
        val devPyPiUser = project.stringPropOrEmpty("devPyPiUser")
        val devPyPiPassword = project.stringPropOrEmpty("devPyPiPassword")

        UploadTaskSpecs(
            DistributionPackageSettings(
                "pip-package",
                "${packageName.replace("-", "_")}-$pythonVersion-py3-none-any.whl"
            ),
            "pyPi",
            PYPI_GROUP,
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

    companion object {
        const val NAME = "kernelBuild"
    }
}
