import build.CreateResourcesTask
import build.util.buildProperties

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
    withTests()
}

CreateResourcesTask.register(project, "buildProperties", tasks.processResources) {
    addPropertiesFile(
        "dependencies-resolution.properties",
        buildProperties {
            add("amperVersion" to libs.versions.amper.get())
        },
    )
}
