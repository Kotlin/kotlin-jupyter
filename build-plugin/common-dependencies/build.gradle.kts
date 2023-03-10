plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.stdlib)

    // HTTP4K for resolving remote library dependencies
    api(libs.bundles.http4k)

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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        apiVersion = "1.4"
        languageVersion = "1.4"

        @Suppress("SuspiciousCollectionReassignment")
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")

        this.jvmTarget = myJvmTarget
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
