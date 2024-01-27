package build

import build.util.TaskSpec
import build.util.UploadTaskSpecs
import build.util.addAllBuildRepositories
import build.util.defaultVersionCatalog
import build.util.getOrCreateExtension
import build.util.isNonStableVersion
import build.util.ktlint
import build.util.makeTaskName
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

internal class KernelBuildConfigurator(private val project: Project) {
    private val settings = project.getOrCreateExtension(RootSettingsExtension)

    fun configure() {
        with(project.plugins) {
            apply("org.jetbrains.kotlin.jvm")
            apply("com.github.johnrengelman.shadow")
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("org.jetbrains.kotlin.libs.publisher")
            apply("org.jetbrains.kotlin.libs.doc")
            apply("org.hildan.github.changelog")
        }

        setupVersionsPlugin()
        setupKtLintForAllProjects()

        println("##teamcity[buildNumber '${settings.pyPackageVersion}']")
        println("##teamcity[setParameter name='mavenVersion' value='${settings.mavenVersion}']")

        project.subprojects {
            // Give to subprojects an ability to access build settings
            extensions.add(RootSettingsExtension.name, settings)
        }

        project.allprojects {
            version = settings.mavenVersion
            addAllBuildRepositories()
            getOrCreateExtension(BuildSettingsExtension).apply {
                withJvmTarget(settings.jvmTarget)
                withLanguageLevel(settings.stableKotlinLanguageLevel)
            }
        }

        project.afterEvaluate {
            configureTasks()
        }
    }

    private fun configureTasks() {
        registerUpdateLibrariesTask()
        registerReadmeTasks()
        registerCompatibilityTableTask()
        registerKotlinVersionUpdateTask()
        registerLibrariesUpdateTasks()

        /****** Build tasks ******/
        registerCleanTasks()
        configureJarTasks()

        /****** Local install ******/
        val installTasksConfigurator = InstallTasksConfigurator(project, settings)
        installTasksConfigurator.registerLocalInstallTasks()

        /****** Distribution ******/
        registerDistributionTasks()
        installTasksConfigurator.registerInstallTasks(false, settings.distribKernelDir, settings.distribBuildDir)
        registerPythonPackageTasks()
        registerAggregateUploadTasks()
    }

    private fun setupVersionsPlugin() {
        project.plugins.apply("com.github.ben-manes.versions")
        project.tasks.withType<DependencyUpdatesTask> {
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
                disabledRules.addAll(
                    "experimental:type-parameter-list-spacing",
                )
            }
        }
    }

    private fun registerUpdateLibrariesTask() {
        project.tasks.register<UpdateLibrariesTask>(UPDATE_LIBRARIES_TASK)
        project.tasks.withType<Test> {
            dependsOn(UPDATE_LIBRARIES_TASK)
        }
    }

    private fun registerCleanTasks() {
        listOf(true, false).forEach { local ->
            val dir = if (local) settings.localInstallDir else settings.distribBuildDir
            project.tasks.register(makeTaskName(settings.cleanInstallDirTaskPrefix, local)) {
                group = if (local) LOCAL_INSTALL_GROUP else DISTRIBUTION_GROUP
                doLast {
                    if (!dir.deleteRecursively()) {
                        throw Exception("Cannot delete $dir")
                    }
                }
            }
        }
    }

    private fun registerDistributionTasks() {
        DistributionTasksConfigurator(project, settings).registerTasks()
    }

    private fun registerPythonPackageTasks() {
        PythonPackageTasksConfigurator(project, settings).registerTasks()
    }

    private fun registerAggregateUploadTasks() {
        val infixToSpec = mapOf<String, (UploadTaskSpecs<*>) -> TaskSpec>(
            "Dev" to { it.dev },
            "Stable" to { it.stable }
        )

        infixToSpec.forEach { (infix, taskSpecGetter) ->
            val tasksList = mutableListOf<String>()
            listOf(settings.condaTaskSpecs, settings.pyPiTaskSpecs).forEach { taskSpec ->
                tasksList.add(taskSpecGetter(taskSpec).taskName)
            }

            if (infix == "Dev") {
                tasksList.add("publishToPluginPortal")
                tasksList.add("publishToSonatypeAndRelease")
                tasksList.add("publishDocs")
            }

            project.tasks.register("aggregate${infix}Upload") {
                group = DISTRIBUTION_GROUP
                dependsOn(tasksList)
            }
        }
    }

    private fun registerKotlinVersionUpdateTask() {
        KernelVersionUpdateTasksConfigurator(project).registerTasks()
    }

    private fun registerLibrariesUpdateTasks() {
        LibraryUpdateTasksConfigurator(project, settings).registerTasks()
    }

    private fun registerReadmeTasks() {
        ReadmeGenerator(project, settings).registerTasks {
            dependsOn(UPDATE_LIBRARIES_TASK)
        }
    }

    private fun registerCompatibilityTableTask() {
        CompatibilityTableGenerator(project, settings).registerTasks { }
    }

    private fun configureJarTasks() {
        val jarTask = project.tasks.named(JAR_TASK, Jar::class.java) {
            manifest {
                attributes["Main-Class"] = settings.mainClassFQN
                attributes["Implementation-Version"] = project.version
            }
        }

        project.tasks.named(SHADOW_JAR_TASK, ShadowJar::class.java) {
            archiveBaseName.set(settings.packageName)
            archiveClassifier.set("all")
            mergeServiceFiles()
            transform(ComponentsXmlResourceTransformer())

            manifest {
                attributes(jarTask.get().manifest.attributes)
            }
        }
    }
}
