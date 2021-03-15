package org.jetbrains.kotlinx.jupyter.exceptions

class ReplPreprocessingException(
    message: String,
    cause: Throwable? = null
) : ReplException(message, cause)
