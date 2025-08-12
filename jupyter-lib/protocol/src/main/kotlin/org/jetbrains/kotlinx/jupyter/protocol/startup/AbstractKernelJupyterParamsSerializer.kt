package org.jetbrains.kotlinx.jupyter.protocol.startup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * This abstract class is fully functional serializer/deserializer for
 * [KernelJupyterParams] from the connection JSON.
 *
 * You only need to implement [deserializePorts] method that should create an instance
 * of [KernelPorts] from the given JSON object.
 *
 * We put this class separately to the protocol module
 * to decouple it from the server-related classes and to provide access to it from
 * intellij.
 */
abstract class AbstractKernelJupyterParamsSerializer : KSerializer<KernelJupyterParams> {
    @Serializable
    private data class KernelJupyterOtherParams(
        @SerialName("signature_scheme")
        val signatureScheme: String = KERNEL_SIGNATURE_SCHEME,
        @SerialName("key")
        val signatureKey: String = "",
        @SerialName("host")
        val host: String = ANY_HOST_NAME,
        @SerialName("transport")
        val transport: String? = KERNEL_TRANSPORT_SCHEME,
    )

    private val utilSerializer = serializer<JsonObject>()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    override val descriptor: SerialDescriptor
        get() = utilSerializer.descriptor

    override fun deserialize(decoder: Decoder): KernelJupyterParams {
        val map = utilSerializer.deserialize(decoder)
        val ports = deserializePorts(map)
        val otherParams = json.decodeFromJsonElement<KernelJupyterOtherParams>(map)

        return KernelJupyterParams(
            signatureScheme = otherParams.signatureScheme,
            signatureKey = otherParams.signatureKey,
            host = otherParams.host,
            ports = ports,
            transport = otherParams.transport,
        )
    }

    override fun serialize(
        encoder: Encoder,
        value: KernelJupyterParams,
    ) {
        val otherParams =
            json
                .encodeToJsonElement(
                    KernelJupyterOtherParams(
                        signatureScheme = value.signatureScheme,
                        signatureKey = value.signatureKey,
                        host = value.host,
                        transport = value.transport,
                    ),
                ).jsonObject
        val ports = value.ports.serialize()
        val map = JsonObject(ports + otherParams)

        utilSerializer.serialize(encoder, map)
    }

    protected abstract fun deserializePorts(map: JsonObject): KernelPorts
}
