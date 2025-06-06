plugins {
    id("com.google.devtools.ksp")
    kotlin("jvm")
    kotlin("jupyter.api")
    kotlin("libs.publisher")
}

kotlinJupyter {
    addApiDependency()
    addScannerDependency()
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
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
