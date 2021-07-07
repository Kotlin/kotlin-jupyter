plugins {
    id("com.gradle.plugin-publish")
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
    id("ru.ileasile.kotlin.publisher")
}

project.version = rootProject.version
project.group = "org.jetbrains.kotlin"

val junitVersion: String by rootProject
val kotlinVersion: String by rootProject
val stableKotlinVersion: String by rootProject
val gradleKotlinVersion: String by rootProject

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Temporary solution until Kotlin 1.4 will be supported in
    // .kts buildscripts and it will be possible to use
    // kotlinx.serialization in plugin code
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$gradleKotlinVersion")
    implementation("com.google.code.gson:gson:2.8.6")

    testImplementation(kotlin("test", gradleKotlinVersion))

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    testImplementation(project(":api"))
    testImplementation(project(":api-annotations"))
}

val saveVersion by tasks.registering {
    inputs.property("version", version)

    val outputDir = file(project.buildDir.toPath().resolve("resources").resolve("main"))
    outputs.dir(outputDir)

    doLast {
        outputDir.mkdirs()
        val propertiesFile = outputDir.resolve("VERSION")
        propertiesFile.writeText(version.toString())
    }
}

tasks {
    processResources {
        dependsOn(saveVersion)
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    register<Jar>("sourceJar") {
        archiveClassifier.set("sources")
        from(sourceSets.named("main").get().allSource)
    }
}

val pluginName = "apiGradlePlugin"

gradlePlugin {
    plugins {
        create(pluginName) {
            id = "org.jetbrains.kotlin.jupyter.api"
            implementationClass = "org.jetbrains.kotlinx.jupyter.api.plugin.ApiGradlePlugin"
        }
    }
}

pluginBundle {
    // These settings are set for the whole plugin bundle
    website = "https://github.com/Kotlin/kotlin-jupyter"
    vcsUrl = "https://github.com/Kotlin/kotlin-jupyter"

    (plugins) {
        pluginName {
            // id is captured from java-gradle-plugin configuration
            displayName = "Kotlin Jupyter kernel integration plugin"
            description = "Gradle plugin providing a smooth Jupyter notebooks integration for Kotlin libraries"
            tags = listOf("jupyter", "kernel", "kotlin")
        }
    }

    mavenCoordinates {
        groupId = "org.jetbrains.kotlin"
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            artifact(tasks["sourceJar"])
        }
    }

    repositories {
        (rootProject.findProperty("localPublicationsRepo") as? java.nio.file.Path)?.let {
            maven {
                name = "LocalBuild"
                url = it.toUri()
            }
        }
    }
}

if (rootProject.findProperty("isMainProject") == true) {
    val thisProjectName = project.name
    rootProject.tasks {
        named("publishLocal") {
            dependsOn(":$thisProjectName:publishAllPublicationsToLocalBuildRepository")
        }
    }
}
