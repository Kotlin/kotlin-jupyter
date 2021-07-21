plugins {
    id("build.plugins.versions")
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // implementation(projects.commonDependencies)
    implementation(libs.jupyter.commonDependencies)
    api(libs.bundles.allGradlePlugins)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
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
