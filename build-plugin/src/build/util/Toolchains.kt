package build.util

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.jvm.toolchain.JavaToolchainService

val Project.javaToolchains: JavaToolchainService get() =
    (this as ExtensionAware).extensions.getByName("javaToolchains") as JavaToolchainService
