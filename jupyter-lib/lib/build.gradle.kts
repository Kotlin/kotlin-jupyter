plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(project(":api"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}

kotlinPublications {
    publication {
        publicationName = "lib"
        artifactId = "kotlin-jupyter-lib"
        description = "Internal part of Kotlin Jupyter API used only inside notebook cells"
        packageName = artifactId
    }
}
