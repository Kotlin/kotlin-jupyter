package build

import build.util.defaultVersionCatalog
import build.util.junitApi
import build.util.junitEngine
import build.util.kotlinTest
import build.util.kotlintestAssertions
import build.util.testImplementation
import build.util.testRuntimeOnly
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class BuildSettingsExtension(private val project: Project) {
    fun withTests(configure: Test.() -> Unit = {}) {
        val deps = project.defaultVersionCatalog.dependencies

        project.dependencies {
            testImplementation(deps.kotlinTest)
            testImplementation(deps.junitApi)
            testImplementation(deps.kotlintestAssertions)
            testRuntimeOnly(deps.junitEngine)
        }

        project.tasks.withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
            maxHeapSize = "4000m"
            configure()
        }
    }

    fun withLanguageLevel(level: String) {
        withLanguageVersion(level)
        withApiVersion(level)
    }


    fun withLanguageVersion(version: String) {
        val kotlinVersion = KotlinVersion.fromVersion(version)
        project.tasks.withType<KotlinCompile> {
            compilerOptions {
                languageVersion.set(kotlinVersion)
            }
        }
    }

    fun withApiVersion(version: String) {
        val kotlinVersion = KotlinVersion.fromVersion(version)
        project.tasks.withType<KotlinCompile> {
            compilerOptions {
                apiVersion.set(kotlinVersion)
            }
        }
    }

    class KotlinCompilerArgsBuilder {
        private val args: MutableList<String> = mutableListOf()

        fun add(arg: String) = args.add(arg)
        fun build(): List<String> = args

        fun skipPrereleaseCheck() = args.add("-Xskip-prerelease-check")
        fun requiresOptIn() = args.add("-opt-in=kotlin.RequiresOptIn")
        fun allowResultReturnType() = args.add("-Xallow-result-return-type")
        fun jdkRelease(release: String) = args.add("-Xjdk-release=$release")

        fun samConversions(type: String) = args.add("-Xsam-conversions=$type")
        fun samConversionsClass() = samConversions("class")

        fun contextSensitiveResolution() = args.add("-Xcontext-sensitive-resolution")
    }

    fun withCompilerArgs(configure: KotlinCompilerArgsBuilder.() -> Unit) {
        val argsList = KotlinCompilerArgsBuilder().apply(configure).build()
        project.tasks.withType<KotlinCompile> {
            compilerOptions {
                freeCompilerArgs.addAll(argsList)
            }
        }
    }

    fun withJvmTarget(target: String) {
        project.tasks.withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(target))
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
