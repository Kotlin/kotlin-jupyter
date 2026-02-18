plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.api)
    api(projects.kernelCompilerApi)
    api(projects.lib)
    api(projects.protocolApi) // For KernelLoggerFactory

    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.dev.scriptingCommon)
    compileOnly(libs.kotlin.dev.scriptingJvm)
    compileOnly(libs.kotlin.dev.scriptingCompilerImplUnshaded) // For skipExtensionsResolutionForImplicitsExceptInnermost
    compileOnly(libs.kotlin.dev.scriptingCompiler) // For configureDefaultRepl
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}
