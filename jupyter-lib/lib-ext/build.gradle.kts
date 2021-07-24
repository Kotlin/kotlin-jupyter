import build.excludeKotlinDependencies
import build.withTests

plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    id("ru.ileasile.kotlin.publisher")
}

project.version = rootProject.version

dependencies {
    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.stable.reflect)

    compileOnly(projects.api)
    implementation(projects.apiAnnotations)
    kapt(projects.apiAnnotations)

    testImplementation(projects.api)

    implementation(libs.bundles.http4k) {
        excludeKotlinDependencies("stdlib-jdk8")
    }

    implementation(libs.ext.jlatex)
    implementation(libs.ext.xmlgraphics.fop)
    implementation(libs.ext.xmlgraphics.batikCodec)
    implementation(libs.ext.xmlgraphics.commons)

    implementation(libs.ext.graphviz)
}

withTests()

kotlinPublications {
    publication {
        publicationName.set("lib-ext")
        description.set("Extended functionality for Kotlin kernel")
    }
}
