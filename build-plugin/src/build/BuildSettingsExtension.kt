package build

import build.util.defaultVersionCatalog
import build.util.junitApi
import build.util.junitEngine
import build.util.kotlinTest
import build.util.testImplementation
import build.util.testRuntimeOnly
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class BuildSettingsExtension(private val project: Project) {
    fun withTests() {
        val deps = project.defaultVersionCatalog.dependencies

        project.dependencies {
            testImplementation(deps.kotlinTest)
            testImplementation(deps.junitApi)
            testRuntimeOnly(deps.junitEngine)
        }

        project.tasks.withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }

    fun withLanguageLevel(level: String) {
        project.tasks.withType<KotlinCompile> {
            kotlinOptions {
                apiVersion = level
                languageVersion = level
            }
        }
    }

    class KotlinCompilerArgsBuilder {
        private val args: MutableList<String> = mutableListOf()

        fun add(arg: String) = args.add(arg)
        fun build(): List<String> = args

        fun skipPrereleaseCheck() = args.add("-Xskip-prerelease-check")
        fun requiresOptIn() = args.add("-Xopt-in=kotlin.RequiresOptIn")
    }

    fun withCompilerArgs(configure: KotlinCompilerArgsBuilder.() -> Unit) {
        val argsList = KotlinCompilerArgsBuilder().apply(configure).build()
        project.tasks.withType<KotlinCompile> {
            kotlinOptions {
                @Suppress("SuspiciousCollectionReassignment")
                freeCompilerArgs += argsList
            }
        }
    }

    fun withJvmTarget(target: String) {
        project.tasks.withType<KotlinCompile> {
            kotlinOptions {
                this.jvmTarget = target
            }
        }

        project.tasks.withType<JavaCompile> {
            sourceCompatibility = target
            targetCompatibility = target
        }
    }

    companion object : SingleInstanceExtensionCompanion<BuildSettingsExtension> {
        override val name = "buildSettings"
        override fun createInstance(project: Project): BuildSettingsExtension {
            return BuildSettingsExtension(project)
        }
    }
}
