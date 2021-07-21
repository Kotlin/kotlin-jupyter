plugins {
    id("ru.ileasile.kotlin.publisher")
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(projects.api)
    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.stable.reflect)
}

kotlinPublications {
    publication {
        publicationName = "lib"
        artifactId = "kotlin-jupyter-lib"
        description = "Internal part of Kotlin Jupyter API used only inside notebook cells"
        packageName = artifactId
    }
}
