@file:Suppress("UnstableApiUsage")

rootProject.name = "kotlin-jupyter-kernel"

pluginManagement {
    repositories {
        val sharedProps =
            java.util.Properties().apply {
                load(File(rootDir, "shared.properties").inputStream())
            }
        gradlePluginPortal()
        sharedProps.getProperty("shared.repositories").split(',').forEach {
            maven(it)
        }
        if (System.getenv("KOTLIN_JUPYTER_USE_MAVEN_LOCAL") != null) {
            mavenLocal()
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-plugin")

projectStructure {
    folder("api-examples") {
        project("getting-started")
    }
    folder("build-plugin") {
        project("common-dependencies")
    }
    folder("jupyter-lib") {
        project("lib")
        project("api")
        folder("dependencies") {
            project("dependencies-resolution")
            project("dependencies-resolution-shadowed")
            project("dependencies-resolution-test")
        }
        project("kotlin-jupyter-api-gradle-plugin")
        project("shared-compiler")
        project("spring-starter")
        project("lib-ext")
        project("protocol")
        project("protocol-api")
        project("test-kit")
        project("test-kit-test")
        project("ws-server")
        project("zmq-protocol")
        project("zmq-server")
    }
}

fun Settings.projectStructure(configuration: ProjectStructure.() -> Unit) {
    val structure = ProjectStructure()
    structure.configuration()
    structure.applyTo(this)
}

class ProjectStructure {
    private val folders = mutableListOf<Folder>()

    fun folder(
        name: String,
        configuration: Folder.() -> Unit,
    ) {
        val folder = Folder(name)
        folder.configuration()
        folders.add(folder)
    }

    fun applyTo(settings: Settings) {
        folders.forEach { it.applyTo(settings, "") }
    }

    inner class Folder(
        private val name: String,
    ) {
        private val subFolders = mutableListOf<Folder>()
        private val projects = mutableListOf<String>()

        fun folder(
            name: String,
            configuration: Folder.() -> Unit,
        ) {
            val subFolder = Folder(name)
            subFolder.configuration()
            subFolders.add(subFolder)
        }

        fun project(name: String) {
            projects.add(name)
        }

        fun applyTo(
            settings: Settings,
            path: String,
        ) {
            val currentPath = if (path.isEmpty()) name else "$path/$name"
            projects.forEach {
                settings.include(it)
                settings.project(":$it").projectDir = file("$currentPath/$it")
            }
            subFolders.forEach { it.applyTo(settings, currentPath) }
        }
    }
}
