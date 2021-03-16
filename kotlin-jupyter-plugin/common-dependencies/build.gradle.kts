plugins {
    id("ru.ileasile.kotlin.publisher")
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

kotlinPublications {
    publication {
        publicationName = "common-dependencies"
        artifactId = "kotlin-jupyter-common-dependencies"
        description = "Notebook API entities used for building kernel documentation"
        packageName = artifactId
    }
}
