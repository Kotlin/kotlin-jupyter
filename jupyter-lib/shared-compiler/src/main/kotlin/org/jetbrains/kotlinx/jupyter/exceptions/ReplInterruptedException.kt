package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.messaging.EXECUTION_INTERRUPTED_MESSAGE

class ReplInterruptedException : ReplException(EXECUTION_INTERRUPTED_MESSAGE)
