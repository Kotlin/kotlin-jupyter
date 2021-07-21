plugins {
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(projects.commonDependencies)
    api(libs.bundles.allGradlePlugins)
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "org.jetbrains.kotlinx.jupyter.dependencies"
            implementationClass = "org.jetbrains.kotlinx.jupyter.plugin.KotlinJupyterGradlePlugin"
        }
    }
}
