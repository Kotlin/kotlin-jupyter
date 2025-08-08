import build.util.LOGBACK_GROUP
import build.util.implementation

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.kotlinJupyterKernel) {
        exclude(group = LOGBACK_GROUP)
    }
    api(libs.jupyterNotebooksParser)
    implementation(libs.kotlin.dev.stdlib)
    implementation(libs.kotlin.dev.scriptingJvm)
    implementation(libs.serialization.json)
    implementation(libs.test.kotlintest.assertions)
    implementation(libs.kotlin.dev.scriptingDependenciesMavenAll)
}

val rootShadowJar = ':' + build.SHADOW_JAR_TASK

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
}

tasks.dokkaGeneratePublicationHtml {
    mustRunAfter(rootShadowJar)
}

kotlinPublications {
    publication {
        publicationName.set("test-kit")
        description.set("Test suite for testing Kotlin kernel library integration")
    }
}

build.CreateResourcesTask.register(
    project,
    "createTestKitResources",
    project.tasks.named<Copy>(build.PROCESS_RESOURCES_TASK),
) {
    addLibrariesFromDir(rootSettings.librariesDir)
}
