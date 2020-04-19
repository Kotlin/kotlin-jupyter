plugins {
    kotlin("jvm")
}

project.version = rootProject.version

dependencies {
    implementation(kotlinDep("kotlin-stdlib"))
    testImplementation("junit:junit:4.12")
    testImplementation(kotlinDep("kotlin-test"))
}

val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions {
    languageVersion = "1.4"
}
