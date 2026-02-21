import build.CreateResourcesTask
import build.UPDATE_LIBRARIES_TASK
import build.util.buildProperties
import build.util.defaultVersionCatalog
import build.util.devKotlin
import build.util.getCurrentBranch
import build.util.getCurrentCommitSha

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(projects.api)
    api(projects.kernelCompilerApi)
    api(projects.lib)
    api(projects.protocolApi) // For KernelLoggerFactory
    api(projects.protocol) // For KernelConfig

    compileOnly(libs.kotlin.stable.stdlib)
    compileOnly(libs.kotlin.dev.scriptingCommon)
    compileOnly(libs.kotlin.dev.scriptingJvm)
    compileOnly(libs.kotlin.dev.scriptingCompilerImplUnshaded) // For skipExtensionsResolutionForImplicitsExceptInnermost
    compileOnly(libs.kotlin.dev.scriptingCompiler) // For configureDefaultRepl
    compileOnly(libs.serialization.json) // For ReplCompilerException
}

buildSettings {
    withLanguageLevel(rootSettings.kotlinLanguageLevel)
    withCompilerArgs {
        skipPrereleaseCheck()
        jdkRelease(rootSettings.jvmTarget)
    }
}

CreateResourcesTask.register(project, "buildProperties", tasks.processResources) {
    dependsOn(rootProject.tasks.named(UPDATE_LIBRARIES_TASK))

    addPropertiesFile(
        "kotlin-jupyter-compiler.properties",
        buildProperties {
            add("version" to rootSettings.pyPackageVersion)
            add("kotlinVersion" to defaultVersionCatalog.versions.devKotlin)
            add("currentBranch" to project.getCurrentBranch())
            add("currentSha" to project.getCurrentCommitSha())
            rootSettings.jvmTargetForSnippets?.let {
                add("jvmTargetForSnippets" to it)
            }
        },
    )

    addLibrariesFromDir(rootSettings.librariesDir)
}
