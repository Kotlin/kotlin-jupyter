package build

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File

class KernelBuildExtension(
    val project: Project
) {
    var kotlinLanguageLevel by project.prop<String>()
    var stableKotlinLanguageLevel by project.prop<String>()
    var jvmTarget by project.prop<String>()

    var githubRepoUser by project.prop<String>()
    var githubRepoName by project.prop<String>()
    var projectRepoUrl by project.prop<String>()
    var docsRepo by project.prop<String>()
    var librariesRepoUrl by project.prop<String>()
    var librariesRepoUserAndName by project.prop<String>()

    var skipReadmeCheck by project.prop<Boolean>()

    val buildCounter by project.prop("build.counter", "100500")
    val buildNumber by project.prop("build.number", "")

    val devCounter by project.prop<String?>("build.devCounter")

    var libName by project.prop<String>("jupyter.lib.name")
    var libParamName by project.prop<String>("jupyter.lib.param.name")
    var libParamValue by project.prop<String>("jupyter.lib.param.value")

    var prGithubUser by project.prop<String>("jupyter.github.user")
    var prGithubToken by project.prop<String>("jupyter.github.token")

    var baseVersion by project.prop<String>()
    var isLocalBuild by project.prop<Boolean>("build.isLocal")

    var jvmTargetForSnippets by project.prop<String?>()

    var packageName: String = project.rootProject.name

    var versionFileName = "VERSION"

    val artifactsDir: File = run {
        val artifactsPath by project.prop(default = "artifacts")
        val artifactsDir = project.rootDir.resolve(artifactsPath)

        if (isLocalBuild) {
            project.delete(artifactsDir)
        }
        return@run artifactsDir
    }

    val pythonVersion: String = project.detectVersion(this)

    val mavenVersion = pythonVersion.toMavenVersion()

    val readmePath: File = project.rootDir.resolve("docs").resolve("README.md")

    private val installPath = project.typedProperty<String?>("installPath")

    val librariesPath = "libraries"
    val librariesPropertiesPath: File = project.rootDir.resolve(librariesPath).resolve(".properties")

    val installPathLocal: File = if (installPath != null) project.file(installPath)
    else project.file(System.getProperty("user.home").toString()).resolve(".ipython/kernels/kotlin")

    val resourcesDir = "resources"
    val distribBuildPath: File = project.rootDir.resolve("build").resolve("distrib-build")
    val logosPath = project.rootDir.resolve(resourcesDir).resolve("logos")
    val nbExtensionPath = project.rootDir.resolve(resourcesDir).resolve("notebook-extension")
    val distributionPath: File by project.extra(project.rootDir.resolve("distrib"))
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

    val isOnProtectedBranch: Boolean
        get() = project.extra["isOnProtectedBranch"] as Boolean

    private val distribUtilsPath: File = project.rootDir.resolve("distrib-util")
    val distribUtilRequirementsPath: File = distribUtilsPath.resolve("requirements-common.txt")
    val distribUtilRequirementsHintsRemPath: File =
        distribUtilsPath.resolve("requirements-hints-remover.txt")
    val removeTypeHints = true
    val typeHintsRemover: File = distribUtilsPath.resolve("remove_type_hints.py")

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
        const val NAME = "options"
    }
}
