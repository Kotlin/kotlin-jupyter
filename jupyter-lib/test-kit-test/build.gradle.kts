plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("jupyter.api")
}

dependencies {
    testImplementation(projects.testKit)
    testImplementation(libs.test.kotlintest.assertions)
}

val rootShadowJar = ':' + build.SHADOW_JAR_TASK

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withTests {
        mustRunAfter(rootShadowJar)
    }
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
}

tasks.processJupyterApiResources {
    libraryProducers = listOf("org.jetbrains.kotlinx.jupyter.testkit.test.integrations.DatetimeTestIntegration")
}
