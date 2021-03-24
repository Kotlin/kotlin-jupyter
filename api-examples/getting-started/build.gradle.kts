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
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
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
