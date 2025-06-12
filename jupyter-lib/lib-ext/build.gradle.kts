import build.util.excludeKotlinDependencies

plugins {
    kotlin("jvm")
    kotlin("jupyter.api")
    kotlin("libs.publisher")
}

dependencies {
    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.stable.reflect)

    compileOnly(projects.api)

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

buildSettings {
    withTests()
    withCompilerArgs {
        requiresOptIn()
        jdkRelease(libs.versions.jvmTarget.get())
    }
}

tasks.processJupyterApiResources {
    libraryProducers =
        listOf(
            "org.jetbrains.kotlinx.jupyter.ext.integration.Integration",
            "org.jetbrains.kotlinx.jupyter.ext.integration.ElapsedTimeIntegration",
        )
}

kotlinPublications {
    publication {
        publicationName.set("lib-ext")
        description.set("Extended functionality for Kotlin kernel")
    }
}
