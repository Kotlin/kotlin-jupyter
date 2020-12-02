import org.jetbrains.kotlin.jupyter.publishing.addPublication

plugins {
    id("org.jetbrains.kotlin.jupyter.publishing")
    kotlin("jvm")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
}

addPublication {
    publicationName = "dependencies"
    artifactId = "notebook-api-dependencies"
    bintrayDescription = "Additional API for Kotlin Jupyter notebooks"
    bintrayPackageName = "kotlin-jupyter-api-dependencies"
}
