plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.api)
    api(projects.kernelCompilerApi)
    api(projects.lib)
    api(projects.protocolApi) // For KernelLoggerFactory

    implementation(libs.kotlin.stable.stdlib)
    implementation(libs.kotlin.dev.scriptingCommon)
    implementation(libs.kotlin.dev.scriptingJvm)
    implementation(libs.kotlin.dev.scriptingCompilerImplUnshaded) // For skipExtensionsResolutionForImplicitsExceptInnermost
    implementation(libs.kotlin.dev.scriptingCompiler) // For configureDefaultRepl
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}
