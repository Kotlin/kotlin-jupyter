import org.jetbrains.kotlinx.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlinx.jupyter.publishing")
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
    description = "Internal part of Kotlin Jupyter API used only inside notebook cells"
    packageName = artifactId
}
