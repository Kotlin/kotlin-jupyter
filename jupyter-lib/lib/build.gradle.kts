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
        publicationName.set("lib")
        description.set("Internal part of Kotlin Jupyter API used only inside notebook cells")
    }
}
