package org.jetbrains.kotlinx.jupyter.protocol.comms

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommCloseCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommMsgCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommOpenCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CommManagerImpl(
    private val connection: CommCommunicationFacility,
) : CommManagerInternal {
    private val commOpenCallbacks = ConcurrentHashMap<String, CommOpenCallback>()
    private val commTargetToIds = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val commIdToComm = ConcurrentHashMap<String, CommImpl>()

    override fun openComm(
        target: String,
        data: JsonObject,
        metadata: JsonObject,
        buffers: List<ByteArray>?,
    ): Comm {
        val id = UUID.randomUUID().toString()
        val newComm = registerNewComm(target, id)

        connection.sendCommOpen(
            commId = id,
            targetName = target,
            data = data,
            metadata = metadata,
            buffers = buffers.orEmpty(),
        )

        return newComm
    }

    override fun processCommOpen(
        commId: String,
        targetName: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ): Comm? {
        val callback = commOpenCallbacks[targetName]
        if (callback == null) {
            // If no callback is registered, we should send `comm_close` immediately in response.
            connection.sendCommClose(
                commId = commId,
                data = commFailureJson("Target $targetName was not registered"),
            )
            return null
        }

        val newComm = registerNewComm(targetName, commId)
        try {
            callback.messageReceived(newComm, data, metadata, buffers)
        } catch (e: Throwable) {
            connection.sendCommClose(
                commId = commId,
                data =
                    commFailureJson(
                        "Unable to crete comm $commId (with target $targetName), exception was thrown: ${e.stackTraceToString()}",
                    ),
            )
            removeComm(commId)
        }

        return newComm
    }

    private fun registerNewComm(
        target: String,
        id: String,
    ): Comm {
        val commIds = commTargetToIds.getOrPut(target) { CopyOnWriteArrayList() }
        val newComm = CommImpl(target, id)
        commIds.add(id)
        commIdToComm[id] = newComm
        return newComm
    }

    override fun closeComm(
        id: String,
        data: JsonObject,
        metadata: JsonObject,
    ) {
        val comm = commIdToComm[id] ?: return
        comm.close(data, notifyClient = true)
    }

    override fun processCommClose(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ) {
        val comm = commIdToComm[commId] ?: return
        comm.close(data, notifyClient = false)
    }

    fun removeComm(id: String) {
        val comm = commIdToComm[id] ?: return
        val commIds = commTargetToIds[comm.target]!!
        commIds.remove(id)
        commIdToComm.remove(id)
    }

    override fun getComms(target: String?): Collection<Comm> =
        if (target == null) {
            commIdToComm.values.toList()
        } else {
            commTargetToIds[target].orEmpty().mapNotNull { commIdToComm[it] }
        }

    override fun processCommMessage(
        commId: String,
        data: JsonObject,
        metadata: JsonElement?,
        buffers: List<ByteArray>,
    ) {
        commIdToComm[commId]?.messageReceived(
            data = data,
            metadata = metadata,
            buffers = buffers,
        )
    }

    override fun registerCommTarget(
        target: String,
        callback: CommOpenCallback,
    ) {
        commOpenCallbacks[target] = callback
    }

    override fun unregisterCommTarget(target: String) {
        commOpenCallbacks.remove(target)
    }

    inner class CommImpl(
        override val target: String,
        override val id: String,
    ) : Comm {
        private val onMessageCallbacks = mutableListOf<CommMsgCallback>()
        private val onCloseCallbacks = mutableListOf<CommCloseCallback>()
        private var closed = false

        private fun assertOpen() {
            if (closed) {
                throw AssertionError("Comm '$target' has been already closed")
            }
        }

        override fun send(
            data: JsonObject,
            metadata: JsonElement?,
            buffers: List<ByteArray>?,
        ) {
            assertOpen()
            connection.sendCommMessage(id, data, metadata, buffers.orEmpty())
        }

        override fun onMessage(action: CommMsgCallback): CommMsgCallback {
            assertOpen()
            onMessageCallbacks.add(action)
            return action
        }

        override fun removeMessageCallback(callback: CommMsgCallback) {
            onMessageCallbacks.remove(callback)
        }

        override fun onClose(action: CommCloseCallback): CommCloseCallback {
            assertOpen()
            onCloseCallbacks.add(action)
            return action
        }

        override fun removeCloseCallback(callback: CommCloseCallback) {
            onCloseCallbacks.remove(callback)
        }

        override fun close(
            data: JsonObject,
            metadata: JsonElement?,
            notifyClient: Boolean,
        ) {
            assertOpen()
            closed = true
            onMessageCallbacks.clear()

            removeComm(id)

            for (callback in onCloseCallbacks) {
                callback.messageReceived(data, metadata)
            }

            if (notifyClient) {
                connection.sendCommClose(
                    commId = id,
                    data = data,
                    metadata = metadata,
                )
            }
        }

        fun messageReceived(
            data: JsonObject,
            metadata: JsonElement?,
            buffers: List<ByteArray>,
        ) {
            if (closed) return

            connection.processCallbacks {
                for (callback in onMessageCallbacks) {
                    callback.messageReceived(data, metadata, buffers)
                }
            }
        }
    }

    companion object {
        private fun commFailureJson(errorMessage: String): JsonObject =
            JsonObject(
                mapOf(
                    "error" to JsonPrimitive(errorMessage),
                ),
            )
    }
}
