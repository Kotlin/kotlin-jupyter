package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlinx.jupyter.api.libraries.Comm
import org.jetbrains.kotlinx.jupyter.api.libraries.CommCloseCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommManager
import org.jetbrains.kotlinx.jupyter.api.libraries.CommMsgCallback
import org.jetbrains.kotlinx.jupyter.api.libraries.CommOpenCallback
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

interface CommManagerInternal : CommManager {
    fun processCommOpen(target: String, id: String, data: JsonObject): Comm
    fun processCommMessage(id: String, data: JsonObject)
    fun processCommClose(id: String, data: JsonObject)
}

class CommManagerImpl(private val connection: JupyterConnectionInternal) : CommManagerInternal {
    private val iopub get() = connection.iopub

    private val commOpenCallbacks = ConcurrentHashMap<String, CommOpenCallback>()
    private val commTargetToIds = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val commIdToComm = ConcurrentHashMap<String, CommImpl>()

    override fun openComm(target: String, data: JsonObject): Comm {
        val id = UUID.randomUUID().toString()
        val newComm = processCommOpen(target, id, data)

        // send comm_open
        iopub.sendSimpleMessage(
            MessageType.COMM_OPEN,
            CommOpen(newComm.id, newComm.target)
        )

        return newComm
    }

    override fun processCommOpen(target: String, id: String, data: JsonObject): Comm {
        val commIds = commTargetToIds.getOrPut(target) { CopyOnWriteArrayList() }
        val newComm = CommImpl(target, id)
        commIds.add(id)
        commIdToComm[id] = newComm

        val callback = commOpenCallbacks[target]
        callback?.invoke(newComm, data)

        return newComm
    }

    override fun closeComm(id: String, data: JsonObject) {
        val comm = commIdToComm[id] ?: return
        comm.close(data, notifyClient = true)
    }

    override fun processCommClose(id: String, data: JsonObject) {
        val comm = commIdToComm[id] ?: return
        comm.close(data, notifyClient = false)
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

    override fun processCommMessage(id: String, data: JsonObject) {
        commIdToComm[id]?.messageReceived(data)
    }

    override fun registerCommTarget(target: String, callback: (Comm, JsonObject) -> Unit) {
        commOpenCallbacks[target] = callback
    }

    override fun unregisterCommTarget(target: String) {
        commOpenCallbacks.remove(target)
    }

    inner class CommImpl(
        override val target: String,
        override val id: String
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
            iopub.sendSimpleMessage(
                MessageType.COMM_MSG,
                CommMsg(id, data)
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
                iopub.sendSimpleMessage(
                    MessageType.COMM_CLOSE,
                    CommClose(id, data)
                )
            }
        }

        fun messageReceived(data: JsonObject) {
            if (closed) return
            for (callback in onMessageCallbacks) {
                callback(data)
            }
        }
    }
}
