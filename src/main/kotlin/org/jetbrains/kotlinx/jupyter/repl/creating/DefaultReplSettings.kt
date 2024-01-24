package org.jetbrains.kotlinx.jupyter.repl.creating

import org.jetbrains.kotlinx.jupyter.ReplConfig
import org.jetbrains.kotlinx.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig

data class DefaultReplSettings(
    val kernelConfig: KernelConfig,
    val replConfig: ReplConfig,
    val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
    val scriptReceivers: List<Any> = emptyList(),
)
