plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(projects.dependenciesResolution)
    testImplementation(projects.commonDependencies)
    testImplementation(libs.coroutines.core)
    testImplementation(libs.logging.slf4j.simple)
}

buildSettings {
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
    withTests()
}
