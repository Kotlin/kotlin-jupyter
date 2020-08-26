plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kotlin-jupyter-deps"))
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "org.jetbrains.kotlin.jupyter.dependencies"
            implementationClass = "org.jetbrains.kotlin.jupyter.plugin.KotlinJupyterBuildDependency"
        }
    }
}
