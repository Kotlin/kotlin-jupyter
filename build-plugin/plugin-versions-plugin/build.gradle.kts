import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.plugin.ktlint)
    implementation(libs.plugin.publisher)
    implementation(libs.plugin.serialization)
    implementation(libs.kotlin.gradle.gradle)

    constraints {
        implementation(kotlin("sam-with-receiver", libs.versions.stableKotlin.get()))
    }
}

val myJvmTarget = libs.versions.jvmTarget.get()
val myJvmTargetInt = myJvmTarget.substringAfter('.').toInt()

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = myJvmTarget
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(myJvmTargetInt))
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
    }
    test {
        allJava.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
}

gradlePlugin {
    plugins {
        create("plugins-versions") {
            id = "build.plugins.versions"
            implementationClass = "build.PluginVersionsPlugin"
        }
    }
}
