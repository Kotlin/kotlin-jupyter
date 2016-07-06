
package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.int
import com.beust.klaxon.string

fun main(vararg args: String) {
    try {
        val cfgFile = args[0]
        val cfgJson = Parser().parse(cfgFile) as JsonObject
        fun JsonObject.getInt(field: String): Int = int(field) ?: throw RuntimeException("Cannot find $field in $cfgFile")

        val sigScheme = cfgJson.string("signature_scheme")
        val key = cfgJson.string("key")

        kernelServer(ConnectionConfig(
                ports = JupyterSockets.values().map { cfgJson.getInt("${it.name}_port") }.toTypedArray(),
                transport = cfgJson.string("transport") ?: "tcp",
                signatureScheme = sigScheme ?: "hmac1-sha256",
                signatureKey = if (sigScheme == null || key == null) "" else key
        ))
    }
    catch (e: Exception) {
        log.error("exception running kernel with args: \"${args.joinToString()}\"", e)
    }
}

fun kernelServer(config: ConnectionConfig) {
    log.info("Starting server: $config")

    JupyterConnection(config).use { conn ->

        log.info("start listening")

        while (!Thread.currentThread().isInterrupted) {

            try {
                conn.heartbeat.onData { send(it, 0) }
                conn.stdin.onData { logWireMessage(it) }
                conn.shell.onMessage { shellMessagesHandler(it) }
                // TODO: consider listening control on a separate thread, as recommended by the kernel protocol
                conn.control.onMessage { shellMessagesHandler(it) }

                Thread.sleep(config.pollingIntervalMillis)
            }
            catch (e: InterruptedException) {}
        }
    }
}

fun JupyterConnection.Socket.shellMessagesHandler(msg: Message) {
    when (msg.header["msg_type"]) {
        "kernel_info_request" ->
            send(makeReplyMessage(msg, "kernel_info_reply",
                    parentHeader = msg.header,
                    content = mapOf(
                            "protocol_version" to protocolVersion,
                            "language" to "kotlin",
                            "language_version" to "1.1-SNAPSHOT"
                    )))
        "history_request" ->
            send(makeReplyMessage(msg, "history_reply",
                    content = mapOf(
                            "history" to listOf<String>() // not implemented
                    )))
        "shutdown_request" -> {
            send(makeReplyMessage(msg, "shutdown_reply",
                    content = mapOf(
                            "restart" to (msg.content["restart"] ?: "false")
                    )))
            Thread.currentThread().interrupt()
        }
        "connect_request" ->
            send(makeReplyMessage(msg, "connection_reply",
                    content = JupyterSockets.values()
                                            .map { Pair("${it.name}_port", connectionConfig.ports[it.ordinal]) }.toMap()))
        else -> send(makeReplyMessage(msg, "unsupported_message_reply"))
    }
}

