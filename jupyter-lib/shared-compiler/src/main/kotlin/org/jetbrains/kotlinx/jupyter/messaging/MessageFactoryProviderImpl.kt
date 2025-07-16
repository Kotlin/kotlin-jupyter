package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.protocol.api.RawMessage
import org.jetbrains.kotlinx.jupyter.util.UpdatableProviderImpl

class MessageFactoryProviderImpl :
    UpdatableProviderImpl<MessageFactory>(),
    MessageFactoryProvider {
    override fun update(rawMessage: RawMessage) {
        update(
            MessageFactoryImpl().apply {
                updateSessionInfo(rawMessage)
                updateContextMessage(rawMessage)
            },
        )
    }
}
