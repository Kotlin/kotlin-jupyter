import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

project.tasks.withType<KotlinCompile> {
    val kotlinVersion = KotlinVersion.fromVersion(version)
    compilerOptions {
        languageVersion.set(kotlinVersion)
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
