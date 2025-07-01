package org.jetbrains.kotlinx.jupyter.util

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.common.CommonLoggerFactory

fun KernelLoggerFactory.asCommonFactory(): CommonLoggerFactory =
    CommonLoggerFactory { clazz ->
        getLogger(clazz)
    }
