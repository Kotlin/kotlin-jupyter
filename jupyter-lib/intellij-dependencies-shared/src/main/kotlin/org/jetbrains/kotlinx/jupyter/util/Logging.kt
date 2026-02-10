package org.jetbrains.kotlinx.jupyter.util

import org.jetbrains.kotlinx.jupyter.common.CommonLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory

fun KernelLoggerFactory.asCommonFactory(): CommonLoggerFactory =
    CommonLoggerFactory { clazz ->
        getLogger(clazz)
    }
