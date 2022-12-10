package org.jetbrains.kotlinx.jupyter.exceptions

open class ReplPreprocessingException(
    message: String,
    cause: Throwable? = null,
) : ReplException(message, cause)
