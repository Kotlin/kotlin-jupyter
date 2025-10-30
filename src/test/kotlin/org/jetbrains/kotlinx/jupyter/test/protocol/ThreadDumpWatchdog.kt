package org.jetbrains.kotlinx.jupyter.test.protocol

import org.jetbrains.kotlinx.jupyter.debug.dumpThreadsToFile
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Encapsulates periodic thread-dump requests to the kernel during long-running tests.
 * Starts after [initialDelaySec] and then repeats every [periodSec] seconds, requesting to dump thread-dumps
 * to [reportsDir] with filenames that include the JUnit display name, by calling [requestThreadDump].
 */
class ThreadDumpWatchdog(
    private val requestThreadDump: (file: File) -> Unit = { file -> dumpThreadsToFile(file) },
    private val reportsDir: Path = Paths.get("build", "reports", "tests", "threadDumps"),
    private val initialDelaySec: Long = 60,
    private val periodSec: Long = 5,
) {
    private var future: ScheduledFuture<*>? = null
    private var scheduler = createScheduledExecutorService()

    private fun createScheduledExecutorService(): ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "thread-dump-watchdog").apply { isDaemon = true }
        }

    fun start(testDisplayName: String) {
        if (future != null) return
        future =
            scheduler.scheduleAtFixedRate(
                {
                    try {
                        dumpThreads(testDisplayName)
                    } catch (_: Throwable) {
                        // Never fail tests because of watchdog issues
                    }
                },
                initialDelaySec,
                periodSec,
                TimeUnit.SECONDS,
            )
    }

    private fun dumpThreads(testDisplayName: String) {
        val reports = reportsDir.toFile()
        reports.mkdirs()
        val ts =
            DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now())
        val safeName = sanitize(testDisplayName)
        val fileName = "$safeName threadDump $ts.txt"
        val filePath = reports.resolve(fileName)
        requestThreadDump(filePath)
    }

    fun stop() {
        try {
            future?.cancel(true)
        } finally {
            future = null
            scheduler.shutdownNow()
        }
    }

    private fun sanitize(name: String): String =
        name
            .replace(Regex("[^A-Za-z0-9._-]+"), " ")
            .trim()
            .replace(' ', '_')
}
