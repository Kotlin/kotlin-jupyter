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
    implementation(kotlin("stdlib"))
}

addPublication {
    publicationName = "common-dependencies"
    artifactId = "kotlin-jupyter-common-dependencies"
    bintrayDescription = "Notebook API entities used for building kernel documentation"
    bintrayPackageName = artifactId
}
