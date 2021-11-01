plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
}

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
