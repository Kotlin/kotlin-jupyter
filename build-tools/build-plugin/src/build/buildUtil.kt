package build

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun Project.withTests() {
    val deps = defaultVersionCatalog.dependencies

    dependencies {
        testImplementation(deps.kotlinTest)
        testImplementation(deps.junitApi)
        testRuntimeOnly(deps.junitEngine)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

fun Project.withLanguageLevel(level: String) {
    tasks.withType<KotlinCompile> {
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

fun Project.withCompilerArgs(configure: KotlinCompilerArgsBuilder.() -> Unit) {
    val argsList = KotlinCompilerArgsBuilder().apply(configure).build()
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            @Suppress("SuspiciousCollectionReassignment")
            freeCompilerArgs += argsList
        }
    }
}

fun Project.withJvmTarget(target: String) {
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            this.jvmTarget = target
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = target
        targetCompatibility = target
    }
}
