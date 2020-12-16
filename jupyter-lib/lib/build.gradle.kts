import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(project(":api"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}

addPublication {
    publicationName = "lib"
    artifactId = "kotlin-jupyter-lib"
    bintrayDescription = "Internal part of Kotlin Jupyter API used only inside notebook cells"
    bintrayPackageName = artifactId
}
