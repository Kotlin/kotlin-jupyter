import org.jetbrains.kotlinx.jupyter.build.excludeKotlinDependencies

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

    testImplementation(libs.kotlin.stable.test)

    testImplementation(libs.test.junit.api)
    testRuntimeOnly(libs.test.junit.engine)

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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kotlinPublications {
    publication {
        publicationName = "lib-ext"
        artifactId = "kotlin-jupyter-lib-ext"
        description = "Extended functionality for Kotlin kernel"
        packageName = artifactId
    }
}
