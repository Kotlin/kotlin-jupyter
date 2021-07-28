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
