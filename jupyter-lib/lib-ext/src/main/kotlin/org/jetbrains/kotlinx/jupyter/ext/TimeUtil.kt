package org.jetbrains.kotlinx.jupyter.ext

import org.jetbrains.kotlinx.jupyter.ext.integration.ElapsedTimeIntegration
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

fun enableTimeMeasuring() {
    ElapsedTimeIntegration.enabled = true
}

@OptIn(ExperimentalTime::class)
fun enableTimeMeasuring(format: (Duration) -> Any) {
    enableTimeMeasuring()
    ElapsedTimeIntegration.format = format
}

fun disableTimeMeasuring() {
    ElapsedTimeIntegration.enabled = false
}
