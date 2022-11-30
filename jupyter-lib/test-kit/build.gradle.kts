plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(projects.kotlinJupyterKernel)
    api(libs.jupyterNotebooksParser)
    implementation(libs.kotlin.dev.scriptingJvm)
    implementation(libs.serialization.json)
    implementation(libs.test.kotlintest.assertions)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withTests()
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
    project.tasks.named<Copy>(build.PROCESS_RESOURCES_TASK)
) {
    addLibrariesFromDir(rootSettings.librariesDir)
}
