plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":kotlin-jupyter-deps"))

    implementation("khttp:khttp:1.0.0")
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "org.jetbrains.kotlin.jupyter.dependencies"
            implementationClass = "org.jetbrains.kotlin.jupyter.plugin.KotlinJupyterGradlePlugin"
        }
    }
}
