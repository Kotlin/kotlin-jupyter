plugins {
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    jcenter()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.5")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.10.2")

    // For maven-publish
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("publishing") {
            id = "org.jetbrains.kotlin.jupyter.publishing"
            implementationClass = "org.jetbrains.kotlin.jupyter.plugin.ApiPublishGradlePlugin"
        }
    }
}
