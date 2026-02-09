package build.util

import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

fun Test.useJavaLauncherOfVersion(version: String) {
    javaLauncher.set(
        project.javaToolchains.launcherFor {
            languageVersion.set(
                JavaLanguageVersion.of(version),
            )
        },
    )
}
