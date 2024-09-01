package org.jetbrains.kotlinx.jupyter.config

import java.io.InputStream
import java.io.PrintStream

class StandardStreams(
    val out: PrintStream,
    val err: PrintStream,
    val `in`: InputStream,
)
