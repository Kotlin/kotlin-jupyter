plugins {
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(kotlinDep("kotlin-stdlib"))
    testImplementation("junit:junit:4.12")
    testImplementation(kotlinDep("kotlin-test"))
}
