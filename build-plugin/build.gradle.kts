import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("build.plugins.versions")
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(projects.commonDependencies)
    api(libs.bundles.allGradlePlugins)
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

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

gradlePlugin {
    plugins {
        create("dependencies") {
            id = "build.plugins.main"
            implementationClass = "build.KernelBuildPlugin"
        }
    }
}
