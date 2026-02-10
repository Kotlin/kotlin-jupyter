plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.api)
    api(projects.kernelCompilerApi)
    api(projects.lib)

    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.dev.scriptingCommon)
    compileOnly(libs.kotlin.dev.scriptingJvm)
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}
