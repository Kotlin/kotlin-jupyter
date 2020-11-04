plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(project(":kotlin-jupyter-deps"))

    implementation("khttp:khttp:1.0.0")
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "org.jetbrains.kotlin.jupyter.dependencies"
            implementationClass = "org.jetbrains.kotlin.jupyter.plugin.KotlinJupyterBuildDependency"
        }
    }
}
