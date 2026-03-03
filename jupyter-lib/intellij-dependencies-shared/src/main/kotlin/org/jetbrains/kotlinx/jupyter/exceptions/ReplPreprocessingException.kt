package org.jetbrains.kotlinx.jupyter.exceptions

import org.jetbrains.kotlinx.jupyter.api.exceptions.ReplException

open class ReplPreprocessingException(
    message: String,
    cause: Throwable? = null,
) : ReplException(message, cause)
