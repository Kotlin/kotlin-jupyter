package build

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.tooling.BuildException
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.jetbrains.kotlinx.jupyter.common.ResponseWrapper
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.jsonObject
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.common.withBasicAuth
import org.jetbrains.kotlinx.jupyter.common.withJson
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream

fun Project.configureKernelBuild() {
    KernelBuildConfigurator(this).configure()
}

@Serializable
class NewPrData(
    val title: String,
    @Suppress("unused")
    val head: String,
    @Suppress("unused")
    val base: String,
)

@Serializable
class SetLabelsData(
    @Suppress("unused")
    val labels: List<String>,
)

private class KernelBuildConfigurator(private val project: Project) {
    private val opts = project.extensions.getOrCreate(KernelBuildExtension.NAME) { KernelBuildExtension(project) }

    private val rootProject = project.rootProject
    private val tasks = project.tasks
    private val configurations = project.configurations
    private val projectDir = project.projectDir

    fun configure() {
        with(project.plugins) {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.johnrengelman.shadow")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("org.jlleitschuh.gradle.ktlint")
            apply("ru.ileasile.kotlin.publisher")
            apply("ru.ileasile.kotlin.doc")
            apply("org.hildan.github.changelog")
        }

        project.version = opts.mavenVersion
        println("##teamcity[buildNumber '${opts.pythonVersion}']")
        println("##teamcity[setParameter name='mavenVersion' value='${opts.mavenVersion}']")

        project.allprojects {
            addAllBuildRepositories()
        }

        project.afterEvaluate {
            doConfigure()
        }
    }

    private fun doConfigure() {
        prepareReadmeTasks()
        prepareKotlinVersionUpdateTasks()

        /****** Build tasks ******/
        preparePropertiesTask()
        createCleanTasks()

        /****** Local install ******/
        prepareLocalTasks()

        /****** Distribution ******/
        prepareDistributionTasks()
        createInstallTasks(false, opts.distribBuildPath.resolve(opts.distribKernelDir), opts.distribBuildPath.resolve(opts.runKernelDir))
        prepareCondaTasks()
        preparePyPiTasks()
        prepareAggregateUploadTasks()


        prepareJarTasks()
    }

    fun createCleanTasks() {
        listOf(true, false).forEach { local ->
            val dir = if (local) opts.installPathLocal else opts.distribBuildPath
            project.task(makeTaskName(opts.cleanInstallDirTaskPrefix, local)) {
                group = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
                doLast {
                    if (!dir.deleteRecursively()) {
                        throw Exception("Cannot delete $dir")
                    }
                }
            }
        }
    }

    fun createInstallTasks(local: Boolean, specPath: File, mainInstallPath: File) {
        val groupName = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
        val cleanDirTask = tasks.getByName(makeTaskName(opts.cleanInstallDirTaskPrefix, local))
        val shadowJar = tasks.getByName(SHADOW_JAR_TASK)

        tasks.register<Copy>(makeTaskName(opts.copyLibrariesTaskPrefix, local)) {
            dependsOn(cleanDirTask)
            group = groupName
            from(opts.librariesPath)
            into(mainInstallPath.resolve(opts.librariesPath))
        }

        tasks.register<Copy>(makeTaskName(opts.installLibsTaskPrefix, local)) {
            dependsOn(cleanDirTask)
            group = groupName
            from(configurations["deploy"])
            into(mainInstallPath.resolve(opts.jarsPath))
        }

        tasks.register<Copy>(makeTaskName(opts.installKernelTaskPrefix, local)) {
            dependsOn(cleanDirTask, shadowJar)
            group = groupName
            from(shadowJar.outputs)
            into(mainInstallPath.resolve(opts.jarsPath))
        }

        listOf(true, false).forEach { debug ->
            val specTaskName = createTaskForSpecs(debug, local, groupName, cleanDirTask, shadowJar, specPath, mainInstallPath)
            createMainInstallTask(debug, local, groupName, specTaskName)
        }
    }

    fun createTaskForSpecs(debug: Boolean, local: Boolean, group: String, cleanDir: Task, shadowJar: Task, specPath: File, mainInstallPath: File): String {
        val taskName = makeTaskName(if (debug) "createDebugSpecs" else "createSpecs", local)
        tasks.register(taskName) {
            this.group = group
            dependsOn(cleanDir, shadowJar)
            doLast {
                val kernelFile = project.files(shadowJar).singleFile

                val libsCp = project.files(configurations["deploy"]).files.map { it.name }

                makeDirs(mainInstallPath.resolve(opts.jarsPath))
                makeDirs(mainInstallPath.resolve(opts.configDir))
                makeDirs(specPath)

                makeJarArgs(mainInstallPath, kernelFile.name, opts.mainClassFQN, libsCp, if (debug) opts.debuggerConfig else "")
                makeKernelSpec(specPath, local)
            }
        }
        return taskName
    }

    fun createMainInstallTask(debug: Boolean, local: Boolean, group: String, specsTaskName: String) {
        val taskNamePrefix = if (local) "install" else "prepare"
        val taskNameMiddle = if (debug) "Debug" else ""
        val taskNameSuffix = if (local) "" else "Package"
        val taskName = "$taskNamePrefix$taskNameMiddle$taskNameSuffix"

        val dependencies = listOf(
            makeTaskName(opts.cleanInstallDirTaskPrefix, local),
            if (local) tasks.getByName(COPY_RUN_KERNEL_PY_TASK) else tasks.getByName(PREPARE_DISTRIBUTION_DIR_TASK),
            makeTaskName(opts.installKernelTaskPrefix, local),
            makeTaskName(opts.installLibsTaskPrefix, local),
            specsTaskName,
            makeTaskName(opts.copyLibrariesTaskPrefix, local)
        )

        project.task(taskName) {
            this.group = group
            dependsOn(dependencies)
        }
    }

    fun makeKernelSpec(installPath: File, localInstall: Boolean) {
        val argv = if (localInstall) {
            listOf(
                "python",
                installPath.resolve(opts.runKernelPy).toString(),
                "{connection_file}",
                installPath.resolve(opts.jarArgsFile).toString(),
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
            installPath.resolve(opts.kernelFile)
        )

        project.copy {
            from(opts.nbExtensionPath, opts.logosPath)
            into(installPath)
        }
    }

    fun makeJarArgs(
        installPath: File,
        kernelJarPath: String,
        mainClassFQN: String,
        classPath: List<String>,
        debuggerConfig: String = ""
    ) {
        writeJson(
            mapOf(
                "mainJar" to kernelJarPath,
                "mainClass" to mainClassFQN,
                "classPath" to classPath,
                "debuggerConfig" to debuggerConfig
            ),
            installPath.resolve(opts.jarArgsFile)
        )
    }



    private fun removeTypeHintsIfNeeded(files: List<File>) {
        if (!opts.removeTypeHints)
            return

        files.forEach {
            val fileName = it.absolutePath
            project.exec {
                commandLine("python", opts.typeHintsRemover, fileName, fileName)
            }
        }
    }

    fun prepareDistributionTasks() {
        tasks.register<PipInstallReq>(INSTALL_COMMON_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = opts.distribUtilRequirementsPath
        }

        tasks.register<PipInstallReq>(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = opts.distribUtilRequirementsHintsRemPath
        }

        tasks.register<Copy>(COPY_DISTRIB_FILES_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(CLEAN_INSTALL_DIR_DISTRIB_TASK)
            if (opts.removeTypeHints) {
                dependsOn(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK)
            }
            from(opts.distributionPath)
            from(opts.readmePath)
            into(opts.distribBuildPath)
            exclude(".idea/**", "venv/**")

            val pythonFiles = mutableListOf<File>()
            eachFile {
                val absPath = opts.distribBuildPath.resolve(this.path).absoluteFile
                if (this.path.endsWith(".py"))
                    pythonFiles.add(absPath)
            }

            doLast {
                removeTypeHintsIfNeeded(pythonFiles)
            }
        }

        project.task(PREPARE_DISTRIBUTION_DIR_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(CLEAN_INSTALL_DIR_DISTRIB_TASK, COPY_DISTRIB_FILES_TASK)
            doLast {
                val versionFilePath = opts.distribBuildPath.resolve(opts.versionFileName)
                versionFilePath.writeText(opts.pythonVersion)
                project.copy {
                    from(versionFilePath)
                    into(opts.artifactsDir)
                }

                opts.distribBuildPath.resolve("REPO_URL").writeText(opts.projectRepoUrl)
            }
        }
    }

    fun prepareCondaTasks() {
        with(opts.condaTaskSpecs) {
            tasks.register<Exec>(CONDA_PACKAGE_TASK) {
                group = CONDA_GROUP
                dependsOn(CLEAN_INSTALL_DIR_DISTRIB_TASK, PREPARE_PACKAGE_TASK)
                commandLine("conda-build", "conda", "--output-folder", packageSettings.dir)
                workingDir(opts.distribBuildPath)
                doLast {
                    project.copy {
                        from(opts.distribBuildPath.resolve(packageSettings.dir).resolve("noarch").resolve(packageSettings.fileName))
                        into(opts.artifactsDir)
                    }
                }
            }

            tasks.named(PUBLISH_LOCAL_TASK) {
                dependsOn(CONDA_PACKAGE_TASK)
            }

            createTasks(opts) { taskSpec ->
                project.task(taskSpec.taskName) {
                    group = CONDA_GROUP
                    val artifactPath = opts.artifactsDir.resolve(packageSettings.fileName)

                    if (!artifactPath.exists()) {
                        dependsOn(CLEAN_INSTALL_DIR_DISTRIB_TASK, CONDA_PACKAGE_TASK)
                    }

                    doLast {
                        project.exec {
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

                        project.exec {
                            commandLine("anaconda", "upload", "-u", taskSpec.username, artifactPath.toString())
                        }
                    }
                }
            }
        }
    }

    fun preparePyPiTasks() {
        with(opts.pyPiTaskSpecs) {
            tasks.register<Exec>(PYPI_PACKAGE_TASK) {
                group = PYPI_GROUP

                dependsOn(PREPARE_PACKAGE_TASK)
                if (opts.isLocalBuild) {
                    dependsOn(INSTALL_COMMON_REQUIREMENTS_TASK)
                }

                commandLine(
                    "python",
                    opts.setupPy,
                    "bdist_wheel",
                    "--dist-dir",
                    packageSettings.dir
                )
                workingDir(opts.distribBuildPath)

                doLast {
                    project.copy {
                        from(opts.distribBuildPath.resolve(packageSettings.dir).resolve(packageSettings.fileName))
                        into(opts.artifactsDir)
                    }
                }
            }

            tasks.named(PUBLISH_LOCAL_TASK) {
                dependsOn(PYPI_PACKAGE_TASK)
            }

            createTasks(opts) { taskSpec ->
                tasks.register<Exec>(taskSpec.taskName) {
                    group = PYPI_GROUP
                    workingDir(opts.artifactsDir)
                    val artifactPath = opts.artifactsDir.resolve(packageSettings.fileName)

                    if (opts.isLocalBuild) {
                        dependsOn(INSTALL_COMMON_REQUIREMENTS_TASK)
                    }
                    if (!artifactPath.exists()) {
                        dependsOn(PYPI_PACKAGE_TASK)
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

    fun prepareAggregateUploadTasks() {
        val infixToSpec = mapOf<String, (UploadTaskSpecs<*>) -> TaskSpec>(
            "Dev" to { it.dev },
            "Stable" to { it.stable }
        )

        infixToSpec.forEach { (infix, taskSpecGetter) ->
            val tasksList = mutableListOf<String>()
            listOf(opts.condaTaskSpecs, opts.pyPiTaskSpecs).forEach { taskSpec ->
                tasksList.add(taskSpecGetter(taskSpec).taskName)
            }

            if (infix == "Dev") {
                tasksList.add("publishToPluginPortal")
                tasksList.add("publishToSonatypeAndRelease")
                tasksList.add("publishDocs")
            }

            tasks.register("aggregate${infix}Upload") {
                group = DISTRIBUTION_GROUP
                dependsOn(tasksList)
            }
        }
    }

    fun prepareLocalTasks() {
        tasks.register<Copy>(COPY_RUN_KERNEL_PY_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(CLEAN_INSTALL_DIR_LOCAL_TASK)
            from(opts.distributionPath.resolve(opts.runKernelDir).resolve(opts.runKernelPy))
            from(opts.distributionPath.resolve(opts.kotlinKernelModule)) {
                into(opts.kotlinKernelModule)
            }
            into(opts.installPathLocal)
        }

        tasks.register<Copy>(COPY_NB_EXTENSION_TASK) {
            group = LOCAL_INSTALL_GROUP
            from(opts.nbExtensionPath)
            into(opts.installPathLocal)
        }

        createInstallTasks(true, opts.installPathLocal, opts.installPathLocal)

        project.task(UNINSTALL_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(CLEAN_INSTALL_DIR_LOCAL_TASK)
        }
    }

    fun prepareKotlinVersionUpdateTasks() {
        tasks.register(UPDATE_KOTLIN_VERSION_TASK) {
            doLast {
                val teamcityProject = PUBLIC_KOTLIN_TEAMCITY
                val teamcityUrl = teamcityProject.url
                val locator = "buildType:(id:${teamcityProject.projectId}),status:SUCCESS,branch:default:any,count:1"

                val response = httpRequest(
                    Request(Method.GET, "$teamcityUrl/$TEAMCITY_REQUEST_ENDPOINT/?locator=$locator")
                        .header("accept", "application/json")
                )
                val builds = response.jsonObject["build"] as JsonArray
                val lastBuild = builds[0] as JsonObject
                val lastBuildNumber = (lastBuild["number"] as JsonPrimitive).content
                println("Last Kotlin dev version: $lastBuildNumber")

                val kotlinVersionProp = "kotlin"
                val gradlePropertiesFile = project.projectDir.resolve("gradle/libs.versions.toml")
                val gradleProperties = gradlePropertiesFile.readLines()
                val updatedGradleProperties = gradleProperties.map {
                    if (it.startsWith("$kotlinVersionProp = ")) "$kotlinVersionProp = \"$lastBuildNumber\""
                    else it
                }
                gradlePropertiesFile.writeText(updatedGradleProperties.joinToString("\n", "", "\n"))
            }
        }

        var updateLibBranchName: String? = null

        val updateLibraryParamTask = tasks.register(UPDATE_LIBRARY_PARAM_TASK) {
            doLast {
                val libName = opts.libName
                val paramName = opts.libParamName
                val paramValue = opts.libParamValue

                updateLibBranchName = "update-$libName-$paramName-$paramValue"
                updateLibraryParam(libName, paramName, paramValue)
            }
        }

        val pushChangesTask = tasks.register(PUSH_CHANGES_TASK) {
            dependsOn(updateLibraryParamTask)

            val librariesDir = projectDir.resolve(opts.librariesPath)
            fun execGit(vararg args: String, configure: ExecSpec.() -> Unit = {}): ExecResult {
                return project.exec {
                    this.executable = "git"
                    this.args = args.asList()
                    this.workingDir = librariesDir

                    configure()
                }
            }

            doLast {
                execGit("config", "user.email", "robot@jetbrains.com")
                execGit("config", "user.name", "robot")

                execGit("add", ".")
                execGit("commit", "-m", "[AUTO] Update library version")

                val currentBranch = project.getPropertyByCommand(
                    "build.libraries.branch",
                    arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD"),
                    librariesDir,
                )
                execGit("push", "--force", "-u", opts.librariesRepoUrl, "$currentBranch:refs/heads/" + updateLibBranchName!!) {
                    this.standardOutput = object : OutputStream() {
                        override fun write(b: Int) { }
                    }
                }

                execGit("reset", "--hard", "HEAD~")
            }
        }

        tasks.register(MAKE_CHANGES_PR_TASK) {
            dependsOn(pushChangesTask)

            doLast {
                val user = opts.prGithubUser
                val password = opts.prGithubToken
                val repoUserAndName = opts.librariesRepoUserAndName
                fun githubRequest(
                    method: Method,
                    request: String,
                    json: JsonElement,
                    onFailure: (Response) -> Unit,
                ): ResponseWrapper {
                    val response = httpRequest(
                        Request(method, "https://api.github.com/$request")
                            .withJson(json)
                            .withBasicAuth(user, password)
                    )
                    println(response.text)
                    if (!response.status.successful) {
                        onFailure(response)
                    }
                    return response
                }

                val prResponse = githubRequest(
                    Method.POST, "repos/$repoUserAndName/pulls",
                    Json.encodeToJsonElement(
                        NewPrData(
                            title = "Update `${opts.libName}` library to `${opts.libParamValue}`",
                            head = updateLibBranchName!!,
                            base = "master"
                        )
                    )
                ) { response ->
                    throw BuildException("Creating PR failed with code ${response.status.code}", null)
                }

                val prNumber = (prResponse.jsonObject["number"] as JsonPrimitive).int
                githubRequest(
                    Method.POST, "repos/$repoUserAndName/issues/$prNumber/labels",
                    Json.encodeToJsonElement(
                        SetLabelsData(listOf("no-changelog", "library-descriptors"))
                    )
                ) { response ->
                    throw BuildException("Cannot setup labels for created PR: ${response.text}", null)
                }
            }
        }
    }

    fun updateLibraryParam(libName: String, paramName: String, paramValue: String) {
        val libFile = project.file(opts.librariesPath).resolve("$libName.json")
        val libText = libFile.readText()
        val paramRegex = Regex("""^([ \t]*"$paramName"[ \t]*:[ \t]*")(.*)("[ \t]*,?)$""", RegexOption.MULTILINE)
        val newText = libText.replace(paramRegex, "$1$paramValue$3")
        libFile.writeText(newText)
    }

    fun preparePropertiesTask() {
        tasks.register(BUILD_PROPERTIES_TASK) {
            group = BUILD_GROUP
            val outputDir = project.file(getSubDir(project.buildDir.toPath(), opts.resourcesDir, opts.mainSourceSetDir))

            inputs.property("version", opts.pythonVersion)
            inputs.property("currentBranch", project.getCurrentBranch())
            inputs.property("currentSha", project.getCurrentCommitSha())
            opts.jvmTargetForSnippets?.let {
                inputs.property("jvmTargetForSnippets", it)
            }

            inputs.file(opts.librariesPropertiesPath)

            outputs.dir(outputDir)

            doLast {
                outputDir.mkdirs()
                val propertiesFile = project.file(getSubDir(outputDir.toPath(), opts.runtimePropertiesFile))

                val properties = inputs.properties.entries.map { it.toPair() }.toMutableList()
                properties.apply {
                    val librariesProperties = readProperties(opts.librariesPropertiesPath)
                    add("librariesFormatVersion" to librariesProperties["formatVersion"])
                }

                propertiesFile.writeText(properties.joinToString("") { "${it.first}=${it.second}\n" })
            }
        }

        tasks.named(PROCESS_RESOURCES_TASK) {
            dependsOn(BUILD_PROPERTIES_TASK)
        }
    }

    fun prepareReadmeTasks() {
        val kotlinVersion = project.defaultVersionCatalog.versions.devKotlin

        val readmeFile = opts.readmePath
        val readmeStubFile = project.rootDir.resolve("docs").resolve("README-STUB.md")
        val librariesDir = rootProject.projectDir.resolve(opts.librariesPath)
        val readmeGenerator = ReadmeGenerator(librariesDir, kotlinVersion, opts.projectRepoUrl)

        fun Task.defineInputs() {
            inputs.file(readmeStubFile)
            inputs.dir(librariesDir)
            inputs.property("kotlinVersion", kotlinVersion)
            inputs.property("projectRepoUrl", opts.projectRepoUrl)
        }

        val generateReadme = tasks.register(GENERATE_README_TASK) {
            group = BUILD_GROUP

            readmeFile.parentFile.mkdirs()

            defineInputs()
            outputs.file(readmeFile)

            doLast {
                readmeGenerator.generate(readmeStubFile, readmeFile)
            }
        }

        tasks.register(CHECK_README_TASK) {
            group = VERIFICATION_GROUP

            defineInputs()
            inputs.file(readmeFile)

            doLast {
                val tempFile = File.createTempFile("kotlin-jupyter-readme", "")
                tempFile.deleteOnExit()
                readmeGenerator.generate(readmeStubFile, tempFile)
                if (tempFile.readText() != readmeFile.readText()) {
                    throw AssertionError("Readme is not regenerated. Regenerate it using `./gradlew ${generateReadme.name}` command")
                }
            }
        }

        tasks.named(CHECK_TASK) {
            if (!opts.skipReadmeCheck) {
                dependsOn(CHECK_README_TASK)
            }
        }
    }

    fun prepareJarTasks() {
        val jarTask = tasks.named(JAR_TASK, Jar::class.java) {
            manifest {
                attributes["Main-Class"] = opts.mainClassFQN
                attributes["Implementation-Version"] = project.version
            }
        }

        tasks.named(SHADOW_JAR_TASK, ShadowJar::class.java) {
            archiveBaseName.set(opts.packageName)
            archiveClassifier.set("")
            mergeServiceFiles()
            transform(ComponentsXmlResourceTransformer())

            manifest {
                attributes(jarTask.get().manifest.attributes)
            }
        }

    }
}
