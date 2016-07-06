
package org.jetbrains.kotlin.jupyter

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.int
import com.beust.klaxon.string
import java.util.concurrent.atomic.AtomicLong

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

        val executionCount = AtomicLong(1)

        while (!Thread.currentThread().isInterrupted) {

            try {
                conn.heartbeat.onData { send(it, 0) }
                conn.stdin.onData { logWireMessage(it) }
                conn.shell.onMessage { shellMessagesHandler(it, conn.iopub, executionCount) }
                // TODO: consider listening control on a separate thread, as recommended by the kernel protocol
                conn.control.onMessage { shellMessagesHandler(it, conn.iopub, executionCount) }

                Thread.sleep(config.pollingIntervalMillis)
            }
            catch (e: InterruptedException) {
                log.info("Interrupted")
                break
            }
        }

        log.info("Shutdown server")
    }
}

fun JupyterConnection.Socket.shellMessagesHandler(msg: Message, iopub: JupyterConnection.Socket, executionCount: AtomicLong) {
    when (msg.header["msg_type"]) {
        "kernel_info_request" ->
            send(makeReplyMessage(msg, "kernel_info_reply",
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
        "execute_request" -> {
            val count = executionCount.getAndIncrement()
            val startedTime = ISO8601DateNow
            with (iopub) {
                send(makeReplyMessage(msg, "status", content = mapOf("execution_state" to "busy")))
                send(makeReplyMessage(msg, "execute_input", content = mapOf(
                        "execution_count" to count,
                        "code" to msg.content["code"])))
                send(makeReplyMessage(msg, "stream", content = mapOf(
                        "name" to "stdout",
                        "text" to "hello, world\n")))
                send(makeReplyMessage(msg, "execute_result", content = mapOf(
                        "execution_count" to count,
                        "data" to JsonObject(mapOf("text/plain" to "result!")),
                        "metadata" to JsonObject())))
                send(makeReplyMessage(msg, "status", content = mapOf("execution_state" to "idle")))
            }
            send(makeReplyMessage(msg, "execute_reply",
                    metadata = mapOf(
                            "dependencies_met" to true,
                            "engine" to msg.header["session"],
                            "status" to "ok",
                            "started" to startedTime),
                    content = mapOf(
                            "status" to "ok",
                            "execution_count" to count,
                            "user_variables" to JsonObject(),
                            "payload" to listOf<String>(),
                            "user_expressions" to JsonObject())
                    ))
        }
        "is_complete_request" -> send(makeReplyMessage(msg, "is_complete_reply", content = mapOf("status" to "complete")))
        else -> send(makeReplyMessage(msg, "unsupported_message_reply"))
    }
}

