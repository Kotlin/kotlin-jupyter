package org.jetbrains.kotlinx.jupyter.compiler.daemon.transport

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.rpc.krpc.KrpcTransport
import kotlinx.rpc.krpc.KrpcTransportMessage
import org.java_websocket.WebSocket
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

abstract class WebSocketKrpcTransportBase :
    KrpcTransport,
    Closeable {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext get() = job

    protected abstract val connection: WebSocket?

    protected val receivedMessages = Channel<ByteArray>(256)

    protected abstract fun closeWebSocket()

    protected fun enqueueMessage(message: String) {
        /** Blocks until [receive] is called if the [receivedMessages] queue is full */
        receivedMessages.trySendBlocking(message.encodeToByteArray())
    }

    protected fun enqueueMessage(message: ByteBuffer) {
        val bytes = ByteArray(message.remaining())
        message.get(bytes)
        /** Blocks until [receive] is called if the [receivedMessages] queue is full */
        receivedMessages.trySendBlocking(bytes).getOrThrow()
    }

    override suspend fun send(message: KrpcTransportMessage) {
        val bytes =
            when (message) {
                is KrpcTransportMessage.BinaryMessage -> message.value
                is KrpcTransportMessage.StringMessage -> message.value.encodeToByteArray()
            }
        (connection ?: throw IllegalStateException("No client connected"))
            .send(bytes)
    }

    override suspend fun receive(): KrpcTransportMessage =
        try {
            KrpcTransportMessage.BinaryMessage(receivedMessages.receive())
        } catch (e: ClosedReceiveChannelException) {
            throw IllegalStateException("Transport is closed", e)
        }

    override fun close() {
        job.cancel()
        receivedMessages.close()
        closeWebSocket()
    }
}
