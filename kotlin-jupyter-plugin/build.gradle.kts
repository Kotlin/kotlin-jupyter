plugins {
    id("org.jlleitschuh.gradle.ktlint")
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":common-dependencies"))

    implementation("khttp:khttp:1.0.0")
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "org.jetbrains.kotlinx.jupyter.dependencies"
            implementationClass = "org.jetbrains.kotlinx.jupyter.plugin.KotlinJupyterGradlePlugin"
        }
    }
}
