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
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.4.20")
    implementation("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.22.0")

    // For maven-publish
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("publishing") {
            id = "org.jetbrains.kotlinx.jupyter.publishing"
            implementationClass = "org.jetbrains.kotlinx.jupyter.plugin.ApiPublishGradlePlugin"
        }
        create("doc") {
            id = "org.jetbrains.kotlinx.jupyter.doc"
            implementationClass = "org.jetbrains.kotlinx.jupyter.plugin.DocGradlePlugin"
        }
    }
}
