package org.jetbrains.kotlinx.jupyter.messaging

import org.jetbrains.kotlinx.jupyter.api.libraries.RawMessage
import org.jetbrains.kotlinx.jupyter.util.UpdatableProviderImpl

class MessageFactoryProviderImpl : MessageFactoryProvider, UpdatableProviderImpl<MessageFactory>() {
    override fun update(rawMessage: RawMessage) {
        update(
            MessageFactoryImpl().apply {
                updateSessionInfo(rawMessage)
                updateContextMessage(rawMessage)
            },
        )
    }
}
