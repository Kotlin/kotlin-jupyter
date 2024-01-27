package org.jetbrains.kotlinx.jupyter.execution

sealed interface ExecutionResult<out T> {
    class Success<out T>(val result: T) : ExecutionResult<T>
    class Failure(val throwable: Throwable) : ExecutionResult<Nothing>
    data object Interrupted : ExecutionResult<Nothing>
}
