plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("ru.ileasile.kotlin.publisher")
}

kotlinJupyter {
    addApiDependency()
    addScannerDependency()
}

project.version = rootProject.version

dependencies {
    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.stable.reflect)
}

kotlinPublications {
    publication {
        publicationName = "example-getting-started"
        artifactId = "kotlin-jupyter-example-getting-started"
        description = "Basic API usage example"
        packageName = artifactId
        publishToSonatype = false
    }
}
