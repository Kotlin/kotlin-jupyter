import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.config.ApiVersion.Companion.KOTLIN_2_1
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

repositories {
    mavenCentral()
    mavenLocal()
}

project.tasks.withType<KotlinCompile> {
    kotlinOptions {
        languageVersion = "2.1" // version
    }
}

//
//buildSettings {
//    withLanguageLevel(rootSettings.kotlinLanguageLevel)
//}

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
