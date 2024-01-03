package org.jetbrains.kotlinx.jupyter.util

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun closeWithTimeout(timeoutMs: Long, doClose: () -> Unit) {
    val closeExecutor = Executors.newSingleThreadExecutor()
    try {
        val future = closeExecutor.submit(doClose)
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            return
        } catch (e: ExecutionException) {
            return
        } catch (e: TimeoutException) {
            future.cancel(true)
            return
        }
    } finally {
        closeExecutor.shutdownNow()
    }
}
