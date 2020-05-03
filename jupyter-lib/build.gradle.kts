plugins {
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("test"))
}
