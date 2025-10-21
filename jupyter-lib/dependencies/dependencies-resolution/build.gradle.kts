plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.kotlin.dev.scriptingCommon)
    implementation(libs.amper.dependencyResolver)
}

buildSettings {
    withCompilerArgs {
        jdkRelease(rootSettings.jvmTarget)
    }
}
