package org.jetbrains.kotlinx.jupyter.repl.config

import org.jetbrains.kotlinx.jupyter.config.DefaultKernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.config.defaultRuntimeProperties
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams

data class DefaultReplSettings(
    val kernelConfig: KernelConfig<KotlinKernelOwnParams>,
    val replConfig: ReplConfig,
    val loggerFactory: KernelLoggerFactory = DefaultKernelLoggerFactory,
    val runtimeProperties: ReplRuntimeProperties = defaultRuntimeProperties,
)
