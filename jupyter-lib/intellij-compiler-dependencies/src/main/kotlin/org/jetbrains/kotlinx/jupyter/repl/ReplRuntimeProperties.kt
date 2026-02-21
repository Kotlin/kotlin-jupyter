package org.jetbrains.kotlinx.jupyter.repl

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion

interface ReplRuntimeProperties {
    val version: KotlinKernelVersion?

    @Deprecated("This parameter is meaningless, do not use")
    val librariesFormatVersion: Int
    val currentBranch: String
    val currentSha: String
    val jvmTargetForSnippets: String

    val kotlinVersion: String
}
