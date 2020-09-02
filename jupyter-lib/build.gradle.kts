plugins {
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(project(":kotlin-jupyter-api"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
}
