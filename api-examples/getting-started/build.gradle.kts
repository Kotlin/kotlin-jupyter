plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("ru.ileasile.kotlin.publisher")
}

kotlinJupyter {
    addApiDependency()
    addScannerDependency()
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
