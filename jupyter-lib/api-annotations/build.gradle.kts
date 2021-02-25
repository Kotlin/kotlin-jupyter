import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlinx.jupyter.publishing")
    kotlin("jvm")
}

project.version = rootProject.version

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(kotlin("reflect"))
}

tasks.withType(KotlinCompile::class.java).all {
    kotlinOptions {
        languageVersion = "1.3"
    }
}

addPublication {
    publicationName = "api-annotations"
    artifactId = "kotlin-jupyter-api-annotations"
    description = "Annotations for adding Kotlin Jupyter notebooks support to Kotlin libraries"
    packageName = artifactId
}
