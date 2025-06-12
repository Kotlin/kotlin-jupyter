import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    kotlin("libs.publisher")
}

kotlinJupyter {
    addApiDependency()
}

// Creates the metadata needed for the library integration to be detected automatically.
tasks.processJupyterApiResources {
    libraryProducers = listOf("org.jetbrains.kotlinx.jupyter.example.GettingStartedIntegration")
}

kotlin {
    compilerOptions {
        apiVersion.set(KOTLIN_2_2)
        languageVersion.set(KOTLIN_2_2)
    }
}

dependencies {
    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.stable.reflect)
}

kotlinPublications {
    publication {
        publicationName.set("example-getting-started")
        description.set("Basic API usage example")
        publishToSonatype.set(false)
    }
}
