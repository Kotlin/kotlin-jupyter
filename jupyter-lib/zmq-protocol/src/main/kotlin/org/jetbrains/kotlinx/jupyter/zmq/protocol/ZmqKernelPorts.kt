package org.jetbrains.kotlinx.jupyter.zmq.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.protocol.api.JupyterSocketType
import org.jetbrains.kotlinx.jupyter.protocol.api.jupyterName
import org.jetbrains.kotlinx.jupyter.protocol.startup.KernelPorts
import org.jetbrains.kotlinx.jupyter.protocol.startup.PortsGenerator
import org.jetbrains.kotlinx.jupyter.protocol.startup.create
import java.util.EnumMap

class ZmqKernelPorts(
    val ports: Map<JupyterSocketType, Int>,
) : KernelPorts {
    companion object {
        inline operator fun invoke(getSocketPort: (JupyterSocketType) -> Int) =
            ZmqKernelPorts(
                ports =
                    EnumMap<JupyterSocketType, Int>(JupyterSocketType::class.java).apply {
                        JupyterSocketType.entries.forEach { socket ->
                            val port = getSocketPort(socket)
                            put(socket, port)
                        }
                    },
            )

        fun tryDeserialize(json: JsonObject): ZmqKernelPorts? {
            return ZmqKernelPorts { socket ->
                val fieldName = socket.zmqPortField
                json[fieldName]?.let { Json.decodeFromJsonElement<Int>(it) }
                    ?: return null
            }
        }

        private val JupyterSocketType.zmqPortField get() = "${jupyterName}_port"
    }

    override fun serialize(): JsonObject =
        buildJsonObject {
            for ((socket, port) in ports) {
                put(socket.zmqPortField, JsonPrimitive(port))
            }
        }

    override fun toString(): String = "ZmqKernelPorts(${serialize()})"
}

fun createRandomZmqKernelPorts() =
    PortsGenerator
        .create(32768, 65536)
        .let { generator -> ZmqKernelPorts { generator.randomPort() } }
