package org.jetbrains.kotlinx.jupyter.messaging.comms

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommCloseCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommMsgCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommOpenCallback
import org.jetbrains.kotlinx.jupyter.messaging.CommClose
import org.jetbrains.kotlinx.jupyter.messaging.CommMsg
import org.jetbrains.kotlinx.jupyter.messaging.CommOpen
import org.jetbrains.kotlinx.jupyter.messaging.JupyterCommunicationFacility
import org.jetbrains.kotlinx.jupyter.messaging.Message
import org.jetbrains.kotlinx.jupyter.messaging.MessageType
import org.jetbrains.kotlinx.jupyter.messaging.doWrappedInBusyIdle
import org.jetbrains.kotlinx.jupyter.messaging.sendSimpleMessageToIoPub
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class CommManagerImpl(private val connection: JupyterCommunicationFacility) : CommManagerInternal {
    private val commOpenCallbacks = ConcurrentHashMap<String, CommOpenCallback>()
    private val commTargetToIds = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val commIdToComm = ConcurrentHashMap<String, CommImpl>()

    override fun openComm(target: String, data: JsonObject): Comm {
        val id = UUID.randomUUID().toString()
        val newComm = registerNewComm(target, id)

        // send comm_open
        connection.sendSimpleMessageToIoPub(
            MessageType.COMM_OPEN,
            CommOpen(newComm.id, newComm.target, data),
        )

        return newComm
    }

    override fun processCommOpen(message: Message, content: CommOpen): Comm? {
        val target = content.targetName
        val id = content.commId
        val data = content.data

        val callback = commOpenCallbacks[target]
        if (callback == null) {
            // If no callback is registered, we should send `comm_close` immediately in response.
            connection.sendSimpleMessageToIoPub(
                MessageType.COMM_CLOSE,
                CommClose(id, commFailureJson("Target $target was not registered")),
            )
            return null
        }

        val newComm = registerNewComm(target, id)
        try {
            callback(newComm, data)
        } catch (e: Throwable) {
            connection.sendSimpleMessageToIoPub(
                MessageType.COMM_CLOSE,
                CommClose(id, commFailureJson("Unable to crete comm $id (with target $target), exception was thrown: ${e.stackTraceToString()}")),
            )
            removeComm(id)
        }

        return newComm
    }

    private fun registerNewComm(target: String, id: String): Comm {
        val commIds = commTargetToIds.getOrPut(target) { CopyOnWriteArrayList() }
        val newComm = CommImpl(target, id)
        commIds.add(id)
        commIdToComm[id] = newComm
        return newComm
    }

    override fun closeComm(id: String, data: JsonObject) {
        val comm = commIdToComm[id] ?: return
        comm.close(data, notifyClient = true)
    }

    override fun processCommClose(message: Message, content: CommClose) {
        val comm = commIdToComm[content.commId] ?: return
        comm.close(content.data, notifyClient = false)
    }

    fun removeComm(id: String) {
        val comm = commIdToComm[id] ?: return
        val commIds = commTargetToIds[comm.target]!!
        commIds.remove(id)
        commIdToComm.remove(id)
    }

    override fun getComms(target: String?): Collection<Comm> {
        return if (target == null) {
            commIdToComm.values.toList()
        } else {
            commTargetToIds[target].orEmpty().mapNotNull { commIdToComm[it] }
        }
    }

    override fun processCommMessage(message: Message, content: CommMsg) {
        commIdToComm[content.commId]?.messageReceived(content.data)
    }

    override fun registerCommTarget(target: String, callback: (Comm, JsonObject) -> Unit) {
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
        override fun send(data: JsonObject) {
            assertOpen()
            connection.sendSimpleMessageToIoPub(
                MessageType.COMM_MSG,
                CommMsg(id, data),
            )
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

        override fun close(data: JsonObject, notifyClient: Boolean) {
            assertOpen()
            closed = true
            onMessageCallbacks.clear()

            removeComm(id)

            onCloseCallbacks.forEach { it(data) }

            if (notifyClient) {
                connection.sendSimpleMessageToIoPub(
                    MessageType.COMM_CLOSE,
                    CommClose(id, data),
                )
            }
        }

        fun messageReceived(data: JsonObject) {
            if (closed) return

            connection.doWrappedInBusyIdle {
                for (callback in onMessageCallbacks) {
                    callback(data)
                }
            }
        }
    }

    companion object {
        private fun commFailureJson(errorMessage: String): JsonObject {
            return JsonObject(
                mapOf(
                    "error" to JsonPrimitive(errorMessage),
                ),
            )
        }
    }
}
