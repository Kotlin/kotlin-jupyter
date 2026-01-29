import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("build.plugins.versions")
    alias(libs.plugins.plugin.kotlin.dsl)
}

dependencies {
    implementation(projects.commonDependencies)
    implementation(libs.jupyter.api)
    api(libs.bundles.allGradlePlugins)

    constraints {
        implementation(kotlin("serialization", libs.versions.stableKotlin.get()))
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
    test {
        allJava.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
}

val myJvmTarget = libs.versions.jvmTarget.get()
val myJvmTargetInt = myJvmTarget.substringAfter('.').toInt()

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(listOf(
            "-opt-in=kotlin.RequiresOptIn",
            // Fix for https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
            // See KT-49746
            "-Xjdk-release=$myJvmTarget",
        ))
        jvmTarget.set(JvmTarget.fromTarget(myJvmTarget))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(myJvmTargetInt))
    }
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "build.plugins.main"
            implementationClass = "build.KernelBuildPlugin"
        }
    }
}
