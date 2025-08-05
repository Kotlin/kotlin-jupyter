package org.jetbrains.kotlinx.jupyter.api.exceptions

/**
 * Basic implementation of [ReplUnwrappedException]
 */
open class ReplUnwrappedExceptionImpl(
    message: String,
    cause: Throwable? = null,
) : ReplMessageOnlyException(message, cause),
    ReplUnwrappedException
