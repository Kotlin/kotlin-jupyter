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

val stableKotlinVersion: String by rootProject

dependencies {
    implementation(kotlin("stdlib", stableKotlinVersion))
    implementation(kotlin("reflect", stableKotlinVersion))
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
