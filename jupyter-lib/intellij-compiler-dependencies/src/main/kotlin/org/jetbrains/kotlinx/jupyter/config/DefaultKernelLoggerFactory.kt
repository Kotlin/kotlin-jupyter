package org.jetbrains.kotlinx.jupyter.config

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DefaultKernelLoggerFactory : KernelLoggerFactory {
    override fun getLogger(category: String): Logger = LoggerFactory.getLogger(category)

    override fun getLogger(clazz: Class<*>): Logger = LoggerFactory.getLogger(clazz)
}
