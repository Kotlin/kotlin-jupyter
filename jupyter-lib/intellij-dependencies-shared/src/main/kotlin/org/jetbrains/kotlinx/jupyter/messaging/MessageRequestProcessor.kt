package org.jetbrains.kotlinx.jupyter.messaging

interface MessageRequestProcessor {
    fun processShellMessage()

    fun processControlMessage()

    fun processStdinMessage()
}
