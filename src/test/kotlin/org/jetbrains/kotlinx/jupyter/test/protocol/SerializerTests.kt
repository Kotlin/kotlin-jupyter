package org.jetbrains.kotlinx.jupyter.test.protocol

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.messaging.UpdateClientMetadataErrorReply
import org.jetbrains.kotlinx.jupyter.messaging.serializers.UpdateClientMetadataReplySerializer
import kotlin.test.Test

/**
 * Class testing aspects of protocol message serialization that cannot be tested by normal usage of the protocol
 */
class SerializerTests {
    @Test
    fun `UpdateClientMetadataErrorReply message should serialize`() {
        val exception = Exception("BOOM")
        val originalMessage = UpdateClientMetadataErrorReply(exception)
        val json = Json.encodeToString(UpdateClientMetadataReplySerializer, originalMessage)

        json shouldContain "\"ename\":\"Exception\""
        json shouldContain "\"evalue\":\"BOOM\""

        val deserializedMessage = Json.decodeFromString(UpdateClientMetadataReplySerializer, json)
        deserializedMessage.shouldBeTypeOf<UpdateClientMetadataErrorReply>()
        originalMessage.status shouldBe deserializedMessage.status
        originalMessage.name shouldBe deserializedMessage.name
        originalMessage.value shouldBe deserializedMessage.value
    }
}
