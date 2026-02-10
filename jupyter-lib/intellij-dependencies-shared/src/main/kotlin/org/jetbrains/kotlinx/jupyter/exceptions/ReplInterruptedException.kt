package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplMessageOnlyException
import org.jetbrains.kotlinx.jupyter.messaging.EXECUTION_INTERRUPTED_MESSAGE

class ReplInterruptedException(
    message: String? = null,
) : ReplMessageOnlyException(message ?: EXECUTION_INTERRUPTED_MESSAGE)
