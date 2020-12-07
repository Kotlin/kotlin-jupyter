import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(project(":kotlin-jupyter-api"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}

addPublication {
    publicationName = "lib"
    artifactId = "notebook-lib"
    bintrayDescription = "Internal Kotlin Jupyter API"
    bintrayPackageName = "kotlin-jupyter-lib"
}
