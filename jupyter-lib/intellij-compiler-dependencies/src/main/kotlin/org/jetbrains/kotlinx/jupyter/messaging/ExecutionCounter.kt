package org.jetbrains.kotlinx.jupyter.messaging

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Class responsible for tracking the execution count that is sent from the kernel to the client for all execution
 * requests. It should be sent back to the client in `execute_reply` and `execute_input` messages.
 **
 * See https://jupyter-client.readthedocs.io/en/stable/messaging.html#execution-counter-prompt-number
 * for further details.
 */
class ExecutionCounter(
    initialValue: Int,
) {
    private val counter = AtomicInteger(initialValue)

    /**
     * Return the next [ExecutionCount].
     *
     * @param storeHistory if `true` the execution count should monotonically increase. If `false` the current
     * value is returned.
     */
    fun next(storeHistory: Boolean): ExecutionCount {
        val value =
            counter.getAndUpdate {
                if (storeHistory) it + 1 else it
            }
        return ExecutionCount(value)
    }
}

/**
 * Typesafe wrapper for execution counts created by [ExecutionCounter].
 */
@JvmInline
@Serializable
value class ExecutionCount(
    val value: Int,
) {
    override fun toString(): String = value.toString()

    companion object {
        // Use this if no count is available
        val NO_COUNT = ExecutionCount(-1)
    }
}
