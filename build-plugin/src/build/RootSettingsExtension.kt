package build

import build.util.BUILD_LIBRARIES
import build.util.CondaCredentials
import build.util.CondaTaskSpec
import build.util.DistributionPackageSettings
import build.util.PyPiTaskSpec
import build.util.UploadTaskSpecs
import build.util.isProtectedBranch
import build.util.prop
import build.util.stringPropOrEmpty
import build.util.typedProperty
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File

class RootSettingsExtension(
    val project: Project
) {
    val kotlinLanguageLevel by project.prop<String>()
    val stableKotlinLanguageLevel by project.prop<String>()
    val jvmTarget by project.prop<String>()

    val githubRepoUser by project.prop<String>()
    val githubRepoName by project.prop<String>()
    val projectRepoUrl by project.prop<String>()
    val docsRepo by project.prop<String>()
    val librariesRepoUrl by project.prop<String>()
    val librariesRepoUserAndName by project.prop<String>()

    val skipReadmeCheck by project.prop<Boolean>()

    val libName by project.prop<String>("jupyter.lib.name")
    val libParamName by project.prop<String>("jupyter.lib.param.name")
    val libParamValue by project.prop<String>("jupyter.lib.param.value")

    val prGithubUser by project.prop<String>("jupyter.github.user")
    val prGithubToken by project.prop<String>("jupyter.github.token")

    val isLocalBuild by project.prop("build.isLocal", false)

    val jvmTargetForSnippets by project.prop<String?>()

    val packageName: String = project.rootProject.name

    val versionFileName: String = "VERSION"

    val artifactsDir: File = run {
        val artifactsPath by project.prop(default = "artifacts")
        val artifactsDir = project.rootDir.resolve(artifactsPath)

        if (isLocalBuild) {
            project.delete(artifactsDir)
        }
        return@run artifactsDir
    }

    val isOnProtectedBranch: Boolean = project.isProtectedBranch()
    val pyPackageVersion: String = detectVersion()
    val mavenVersion: String = pyPackageVersion.toMavenVersion()

    val readmeFile: File = project.rootDir.resolve("docs").resolve("README.md")
    val readmeStubFile: File = project.rootDir.resolve("docs").resolve("README-STUB.md")

    val librariesDir: File = BUILD_LIBRARIES.localLibrariesDir

    val localInstallDir: File = run {
        val installPath = project.typedProperty<String?>("installPath")
        if (installPath != null) project.file(installPath)
        else project.file(System.getProperty("user.home").toString()).resolve(".ipython/kernels/kotlin")
    }

    val resourcesDir: File = project.file("resources")
    val distribBuildDir: File = project.buildDir.resolve("distrib-build")
    val distribKernelDir: File = distribBuildDir.resolve("kernel")
    val runKernelDir: File = distribBuildDir.resolve("run_kotlin_kernel")
    val logosDir: File = resourcesDir.resolve("logos")
    val nbExtensionDir: File = resourcesDir.resolve("notebook-extension")
    val distributionDir: File = project.file("distrib")

    val mainClassFQN: String = "org.jetbrains.kotlinx.jupyter.IkotlinKt"

    val jarsPath: String = "jars"
    val configDir: String = "config"
    val jarArgsFile: String = "$configDir/jar_args.json"
    val runKernelPy: String = "run_kernel.py"
    val kotlinKernelModule: String = "kotlin_kernel"
    val kernelFile: String = "kernel.json"
    val setupPy: String = "setup.py"

    val installKernelTaskPrefix: String = "installKernel"
    val cleanInstallDirTaskPrefix: String = "cleanInstallDir"
    val copyLibrariesTaskPrefix: String = "copyLibraries"
    val installLibsTaskPrefix: String = "installLibs"

    private val debugPort = 1044
    val debuggerConfig = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort"

    val runtimePropertiesFile = "runtime.properties"

    private val distribUtilsDir: File = project.file("distrib-util")
    val distribUtilRequirementsFile: File = distribUtilsDir.resolve("requirements-common.txt")
    val distribUtilRequirementsHintsRemoverFile: File = distribUtilsDir.resolve("requirements-hints-remover.txt")
    val removeTypeHints: Boolean = true
    val typeHintsRemover: File = distribUtilsDir.resolve("remove_type_hints.py")

    val condaTaskSpecs by lazy {
        val condaUserStable = project.stringPropOrEmpty("condaUserStable")
        val condaPasswordStable = project.stringPropOrEmpty("condaPasswordStable")
        val condaUserDev = project.stringPropOrEmpty("condaUserDev")

        val condaCredentials = CondaCredentials(condaUserStable, condaPasswordStable)
        UploadTaskSpecs(
            DistributionPackageSettings(
                "conda-package",
                "$packageName-$pyPackageVersion-py_0.tar.bz2"
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
                "${packageName.replace("-", "_")}-$pyPackageVersion-py3-none-any.whl"
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

    private fun String.toMavenVersion(): String {
        val match = BUILD_NUMBER_REGEX.find(this)!!
        val base = match.groups["base"]!!.value
        val counter = match.groups["counter"]!!.value
        val devCounter = match.groups["devCounter"]?.value
        val devAddition = if (devCounter == null) "" else "-$devCounter"

        return "$base-$counter$devAddition"
    }

    private fun detectVersion(): String {
        val buildCounter by project.prop("build.counter", "100500")
        val buildNumber by project.prop("build.number", "")
        val devCounterOrNull by project.prop<String?>("build.devCounter")
        val devCounter = devCounterOrNull ?: "1"
        val baseVersion by project.prop<String>()

        val devAddition = if (isOnProtectedBranch && devCounterOrNull == null) "" else ".dev$devCounter"

        val defaultBuildNumber = "$baseVersion.$buildCounter$devAddition"

        return if (!buildNumber.matches(BUILD_NUMBER_REGEX)) {
            val versionFile = artifactsDir.resolve(versionFileName)
            if (versionFile.exists()) {
                val lines = versionFile.readLines()
                assert(lines.isNotEmpty()) { "There should be at least one line in VERSION file" }
                lines.first().trim()
            } else {
                defaultBuildNumber
            }
        } else {
            buildNumber
        }
    }

    companion object : SingleInstanceExtensionCompanion<RootSettingsExtension> {
        override val name = "rootSettings"
        override fun createInstance(project: Project): RootSettingsExtension {
            return RootSettingsExtension(project)
        }

        private val BUILD_NUMBER_REGEX = Regex("""(?<base>\d+\.\d+\.\d+)\.(?<counter>\d+)(\.dev(?<devCounter>\d+))?""")
    }
}
