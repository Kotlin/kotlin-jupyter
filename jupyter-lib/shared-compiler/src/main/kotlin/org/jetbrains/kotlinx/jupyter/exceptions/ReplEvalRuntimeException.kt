package org.jetbrains.kotlinx.jupyter.exceptions

class ReplEvalRuntimeException(message: String, cause: Throwable? = null) : ReplException(message, cause) {
    override fun render(): String {
        return cause?.messageAndStackTrace() ?: messageAndStackTrace()
    }
}
