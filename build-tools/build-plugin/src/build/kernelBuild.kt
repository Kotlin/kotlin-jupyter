package build

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
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
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
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
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream

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

internal class KernelBuildConfigurator(private val project: Project) {
    private val opts = project.getOrCreateExtension(RootSettingsExtension)

    private val rootProject = project.rootProject
    private val tasks = project.tasks
    private val configurations = project.configurations
    private val projectDir = project.projectDir

    fun configure() {
        with(project.plugins) {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.johnrengelman.shadow")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("ru.ileasile.kotlin.publisher")
            apply("ru.ileasile.kotlin.doc")
            apply("org.hildan.github.changelog")
        }

        setupVersionsPlugin()
        setupKtLintForAllProjects()

        println("##teamcity[buildNumber '${opts.pyPackageVersion}']")
        println("##teamcity[setParameter name='mavenVersion' value='${opts.mavenVersion}']")

        project.subprojects {
            // Give to subprojects an ability to access build options
            extensions.add(RootSettingsExtension.name, opts)
        }

        project.allprojects {
            version = opts.mavenVersion
            addAllBuildRepositories()
            getOrCreateExtension(BuildSettingsExtension).apply {
                withJvmTarget(opts.jvmTarget)
                withLanguageLevel(opts.stableKotlinLanguageLevel)
            }
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
        createInstallTasks(false, opts.distribBuildDir.resolve(opts.distribKernelDir), opts.distribBuildDir.resolve(opts.runKernelDir))
        prepareCondaTasks()
        preparePyPiTasks()
        prepareAggregateUploadTasks()

        prepareJarTasks()
    }

    private fun setupVersionsPlugin() {
        project.plugins.apply("com.github.ben-manes.versions")
        tasks.withType<DependencyUpdatesTask> {
            rejectVersionIf {
                isNonStableVersion(candidate.version) && !isNonStableVersion(currentVersion)
            }
        }
    }

    private fun setupKtLintForAllProjects() {
        val ktlintVersion = project.defaultVersionCatalog.versions.ktlint
        project.allprojects {
            plugins.apply("org.jlleitschuh.gradle.ktlint")
            extensions.configure<KtlintExtension> {
                version.set(ktlintVersion)
                enableExperimentalRules.set(true)
            }
        }
    }

    private fun createCleanTasks() {
        listOf(true, false).forEach { local ->
            val dir = if (local) opts.localInstallDir else opts.distribBuildDir
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

    private fun createInstallTasks(local: Boolean, specPath: File, mainInstallPath: File) {
        val groupName = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
        val cleanDirTask = tasks.getByName(makeTaskName(opts.cleanInstallDirTaskPrefix, local))
        val shadowJar = tasks.getByName(SHADOW_JAR_TASK)

        tasks.register<Copy>(makeTaskName(opts.copyLibrariesTaskPrefix, local)) {
            dependsOn(cleanDirTask)
            group = groupName
            from(opts.librariesDir)
            into(mainInstallPath.resolve(opts.librariesDir))
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

    private fun createTaskForSpecs(debug: Boolean, local: Boolean, group: String, cleanDir: Task, shadowJar: Task, specPath: File, mainInstallPath: File): String {
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

                writeJson(
                    mapOf(
                        "mainJar" to kernelFile.name,
                        "mainClass" to opts.mainClassFQN,
                        "classPath" to libsCp,
                        "debuggerConfig" to if (debug) opts.debuggerConfig else ""
                    ),
                    mainInstallPath.resolve(opts.jarArgsFile)
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

    private fun makeKernelSpec(installPath: File, localInstall: Boolean) {
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
            from(opts.nbExtensionDir, opts.logosDir)
            into(installPath)
        }
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

    private fun prepareDistributionTasks() {
        tasks.register<PipInstallReq>(INSTALL_COMMON_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = opts.distribUtilRequirementsFile
        }

        tasks.register<PipInstallReq>(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK) {
            group = DISTRIBUTION_GROUP
            requirementsFile = opts.distribUtilRequirementsHintsRemoverFile
        }

        tasks.register<Copy>(COPY_DISTRIB_FILES_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, false))
            if (opts.removeTypeHints) {
                dependsOn(INSTALL_HINT_REMOVER_REQUIREMENTS_TASK)
            }
            from(opts.distributionDir)
            from(opts.readmeFile)
            into(opts.distribBuildDir)
            exclude(".idea/**", "venv/**")

            val pythonFiles = mutableListOf<File>()
            eachFile {
                val absPath = opts.distribBuildDir.resolve(this.path).absoluteFile
                if (this.path.endsWith(".py"))
                    pythonFiles.add(absPath)
            }

            doLast {
                removeTypeHintsIfNeeded(pythonFiles)
            }
        }

        project.task(PREPARE_DISTRIBUTION_DIR_TASK) {
            group = DISTRIBUTION_GROUP
            dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, false), COPY_DISTRIB_FILES_TASK)
            doLast {
                val versionFilePath = opts.distribBuildDir.resolve(opts.versionFileName)
                versionFilePath.writeText(opts.pyPackageVersion)
                project.copy {
                    from(versionFilePath)
                    into(opts.artifactsDir)
                }

                opts.distribBuildDir.resolve("REPO_URL").writeText(opts.projectRepoUrl)
            }
        }
    }

    private fun prepareCondaTasks() {
        with(opts.condaTaskSpecs) {
            tasks.register<Exec>(CONDA_PACKAGE_TASK) {
                group = CONDA_GROUP
                dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, false), PREPARE_PACKAGE_TASK)
                commandLine("conda-build", "conda", "--output-folder", packageSettings.dir)
                workingDir(opts.distribBuildDir)
                doLast {
                    project.copy {
                        from(opts.distribBuildDir.resolve(packageSettings.dir).resolve("noarch").resolve(packageSettings.fileName))
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
                        dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, false), CONDA_PACKAGE_TASK)
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

    private fun preparePyPiTasks() {
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
                workingDir(opts.distribBuildDir)

                doLast {
                    project.copy {
                        from(opts.distribBuildDir.resolve(packageSettings.dir).resolve(packageSettings.fileName))
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

    private fun prepareAggregateUploadTasks() {
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

    private fun prepareLocalTasks() {
        tasks.register<Copy>(COPY_RUN_KERNEL_PY_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, true))
            from(opts.distributionDir.resolve(opts.runKernelDir).resolve(opts.runKernelPy))
            from(opts.distributionDir.resolve(opts.kotlinKernelModule)) {
                into(opts.kotlinKernelModule)
            }
            into(opts.localInstallDir)
        }

        tasks.register<Copy>(COPY_NB_EXTENSION_TASK) {
            group = LOCAL_INSTALL_GROUP
            from(opts.nbExtensionDir)
            into(opts.localInstallDir)
        }

        createInstallTasks(true, opts.localInstallDir, opts.localInstallDir)

        project.task(UNINSTALL_TASK) {
            group = LOCAL_INSTALL_GROUP
            dependsOn(makeTaskName(opts.cleanInstallDirTaskPrefix, false))
        }
    }

    private fun prepareKotlinVersionUpdateTasks() {
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

            val librariesDir = projectDir.resolve(opts.librariesDir)
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

    private fun updateLibraryParam(libName: String, paramName: String, paramValue: String) {
        val libFile = project.file(opts.librariesDir).resolve("$libName.json")
        val libText = libFile.readText()
        val paramRegex = Regex("""^([ \t]*"$paramName"[ \t]*:[ \t]*")(.*)("[ \t]*,?)$""", RegexOption.MULTILINE)
        val newText = libText.replace(paramRegex, "$1$paramValue$3")
        libFile.writeText(newText)
    }

    private fun preparePropertiesTask() {
        val properties = buildProperties {
            add("version" to opts.pyPackageVersion)
            add("currentBranch" to project.getCurrentBranch())
            add("currentSha" to project.getCurrentCommitSha())
            opts.jvmTargetForSnippets?.let {
                add("jvmTargetForSnippets" to it)
            }
            val librariesProperties = readProperties(opts.librariesPropertiesFile)
            add("librariesFormatVersion" to librariesProperties["formatVersion"].orEmpty())
        }

        tasks.create<CreateResourcesTask>(BUILD_PROPERTIES_TASK) {
            addPropertiesFile(opts.runtimePropertiesFile, properties)
            setupDependencies(tasks.named<Copy>(PROCESS_RESOURCES_TASK))
        }
    }

    private fun prepareReadmeTasks() {
        val kotlinVersion = project.defaultVersionCatalog.versions.devKotlin

        val readmeFile = opts.readmeFile
        val readmeStubFile = opts.readmeStubFile
        val librariesDir = rootProject.projectDir.resolve(opts.librariesDir)
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

    private fun prepareJarTasks() {
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
