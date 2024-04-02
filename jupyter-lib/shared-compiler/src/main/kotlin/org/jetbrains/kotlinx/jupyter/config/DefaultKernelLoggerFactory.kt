package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.api.KernelLoggerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DefaultKernelLoggerFactory : KernelLoggerFactory {
    override fun getLogger(category: String): Logger {
        return LoggerFactory.getLogger(category)
    }

    override fun getLogger(clazz: Class<*>): Logger {
        return LoggerFactory.getLogger(clazz)
    }
}
