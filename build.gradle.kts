@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Path
import java.nio.file.Paths

val packageName by extra("kotlin-jupyter-kernel")
val baseVersion: String by project

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
}

class TaskOptions: AllOptions {
    override val versionFileName = "VERSION"
    override val rootPath: Path = rootDir.toPath()

    override val isLocalBuild = (rootProject.findProperty("build.isLocal") ?: "false") == "true"

    override val artifactsDir: Path = {
        val artifactsPathStr = rootProject.findProperty("artifactsPath") as? String ?: "artifacts"
        val artifactsDir = rootPath.resolve(artifactsPathStr)

        if (isLocalBuild)
            project.delete(artifactsDir)

        project.version = detectVersion(baseVersion, artifactsDir, versionFileName)
        println("##teamcity[buildNumber '$version']")
        artifactsDir
    }()

    private val installPath = rootProject.findProperty("installPath") as String?

    override val librariesPath = "libraries"
    override val installPathLocal: Path = if (installPath != null)
        Paths.get(installPath)
    else
        Paths.get(System.getProperty("user.home").toString(), ".ipython", "kernels", "kotlin")

    override val resourcesDir = "resources"
    override val distribBuildPath: Path = rootPath.resolve("distrib-build")
    override val logosPath = getSubDir(rootPath, resourcesDir, "logos")
    override val nbExtensionPath = getSubDir(rootPath, resourcesDir, "notebook-extension")
    override val distributionPath: Path by extra(rootPath.resolve("distrib"))
    override val jarsPath = "jars"
    override val configDir = "config"

    // Straight slash is used 'cause it's universal across the platforms, and is used in jar_args config
    override val jarArgsFile = "$configDir/jar_args.json"
    override val runKernelPy = "run_kernel.py"
    override val kernelFile = "kernel.json"
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
    override val distribUtilRequirementsHintsRemPath: Path = distribUtilsPath.resolve("requirements-hints-remover.txt")
    override val removeTypeHints = true
    override val typeHintsRemover: Path = distribUtilsPath.resolve("remove_type_hints.py")

    override val condaTaskSpecs = {
        val condaUserStable = stringPropOrEmpty("condaUserStable")
        val condaPasswordStable = stringPropOrEmpty("condaPasswordStable")
        val condaUserDev = stringPropOrEmpty("condaUserDev")

        val condaPackageSettings = object : DistributionPackageSettings {
            override val dir = "conda-package"
            override val name = packageName
            override val fileName by lazy {"$name-${version}-py_0.tar.bz2"}
        }

        val condaCredentials = CondaCredentials(condaUserStable, condaPasswordStable)
        UploadTaskSpecs(
                condaPackageSettings,"conda", condaGroup,
                CondaTaskSpec(
                        condaUserStable,
                        condaCredentials
                ),
                CondaTaskSpec(
                        condaUserDev,
                        condaCredentials
                )
        )
    }()

    override val pyPiTaskSpecs = {
        val stablePyPiUser = stringPropOrEmpty("stablePyPiUser")
        val stablePyPiPassword = stringPropOrEmpty("stablePyPiPassword")
        val devPyPiUser = stringPropOrEmpty("devPyPiUser")
        val devPyPiPassword = stringPropOrEmpty("devPyPiPassword")

        val pyPiPackageSettings = object : DistributionPackageSettings {
            override val dir = "pip-package"
            override val name = packageName.replace("-", "_")
            override val fileName by lazy {"$name-${version}-py3-none-any.whl"}
        }

        UploadTaskSpecs(
                pyPiPackageSettings, "pyPi", pyPiGroup,
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
    }()

}

allprojects {
    val kotlinLanguageLevel: String by rootProject
    val jvmTarget: String by rootProject

    tasks.withType(KotlinCompile::class.java).all {
        kotlinOptions {
            languageVersion = kotlinLanguageLevel
            this.jvmTarget = jvmTarget
        }
    }

    repositories {
        jcenter()
        mavenLocal()
        mavenCentral()
        // only when using Kotlin EAP releases ...
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-eap") }
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
        maven { url = uri("https://kotlin.bintray.com/kotlin-dependencies") }
    }
}

val deploy: Configuration by configurations.creating

dependencies {
    implementation(kotlinDep("kotlin-stdlib"))
    testImplementation("junit:junit:4.12")
    testImplementation(kotlinDep("kotlin-test"))

    implementation(project(":jupyter-lib"))
    implementation(kotlinDep("kotlin-reflect"))
    implementation(kotlinDep("kotlin-scripting-ide-services-embeddable")) { isTransitive = false }
    implementation(kotlinDep("kotlin-scripting-common"))
    implementation(kotlinDep("kotlin-scripting-compiler-embeddable"))
    implementation(kotlinDep("kotlin-compiler-embeddable"))
    implementation(kotlinDep("kotlin-stdlib-jdk8"))
    implementation(kotlinDep("kotlin-script-util"))
    implementation(kotlinDep("kotlin-scripting-dependencies"))
    implementation(kotlinDep("kotlin-scripting-dependencies-maven"))
    implementation(kotlinDep("kotlin-main-kts"))

    implementation("org.apache.maven:maven-core:3.0.3")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("khttp:khttp:1.0.0")
    implementation("org.zeromq:jeromq:0.3.5")
    implementation("com.beust:klaxon:5.2")
    implementation("com.github.ajalt:clikt:2.3.0")
    runtimeOnly("org.slf4j:slf4j-simple:1.7.25")
    runtimeOnly("org.jetbrains.kotlin:jcabi-aether:1.0-dev-3")
    runtimeOnly("org.sonatype.aether:aether-api:1.13.1")
    runtimeOnly("net.java.dev.jna:jna:5.4.0")

    deploy(project(":jupyter-lib"))
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlin.jupyter.IkotlinKt"
        attributes["Implementation-Version"] = project.version
    }
}

with(ProjectWithOptionsImpl(project, TaskOptions())) {
    /****** Build tasks ******/
    tasks.withType<ShadowJar> {
        archiveBaseName.set(packageName)
        archiveClassifier.set("")
        mergeServiceFiles()

        manifest {
            attributes["Main-Class"] = "org.jetbrains.kotlin.jupyter.IkotlinKt"
        }
    }

    task("buildProperties") {
        group = buildGroup
        val outputDir = file(getSubDir(buildDir.toPath(), resourcesDir, mainSourceSetDir))

        inputs.property("version", version)
        outputs.dir(outputDir)

        doLast {
            outputDir.mkdirs()
            val propertiesFile = file(getSubDir(outputDir.toPath(), runtimePropertiesFile))
            propertiesFile.writeText(inputs.properties.entries.joinToString("") { "${it.key}=${it.value}\n" })
        }
    }

    tasks.processResources {
        dependsOn("buildProperties")
    }

    createCleanTasks()

    /****** Local install ******/
    prepareLocalTasks()

    /****** Distribution ******/
    prepareDistributionTasks()
    createInstallTasks(false, distribBuildPath.resolve(distribKernelDir), distribBuildPath.resolve(runKernelDir))
    prepareCondaTasks()
    preparePyPiTasks()
}
