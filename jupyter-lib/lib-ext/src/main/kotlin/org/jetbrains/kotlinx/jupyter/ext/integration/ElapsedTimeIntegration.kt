package org.jetbrains.kotlinx.jupyter.ext.integration

import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@JupyterLibrary
@OptIn(ExperimentalTime::class)
object ElapsedTimeIntegration : JupyterIntegration() {
    private var lastTime: Long = -1L

    var enabled = false
    var format: (Duration) -> Any = { duration -> "Elapsed time: $duration" }

    override fun Builder.onLoaded() {
        beforeCellExecution {
            if (enabled) {
                lastTime = currentTime()
            }
        }

        afterCellExecution { _, _ ->
            if (enabled && lastTime != -1L) {
                val elapsed = currentTime() - lastTime
                val duration = Duration.milliseconds(elapsed)
                display(format(duration))
            }
        }
    }

    private fun currentTime(): Long = System.currentTimeMillis()
}
