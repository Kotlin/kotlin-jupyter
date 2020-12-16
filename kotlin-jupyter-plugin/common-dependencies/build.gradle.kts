import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
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
