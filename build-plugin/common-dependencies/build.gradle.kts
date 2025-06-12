import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
}

dependencies {
    implementation(libs.kotlin.gradle.stdlib)

    api(libs.logging.slf4j.api)

    // Serialization implementation for kernel code
    api(libs.serialization.json)
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

val myJvmTarget = libs.versions.jvmTarget.get()
val myJvmTargetInt = myJvmTarget.substringAfter('.').toInt()

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        this.jvmTarget.set(JvmTarget.fromTarget(myJvmTarget))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(myJvmTargetInt))
    }
}

kotlinPublications {
    publication {
        publicationName.set("common-dependencies")
        description.set("Notebook API entities used for building kernel documentation")
    }
}
