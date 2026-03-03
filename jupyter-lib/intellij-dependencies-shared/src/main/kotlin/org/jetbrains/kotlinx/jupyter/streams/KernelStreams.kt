package org.jetbrains.kotlinx.jupyter.streams

import java.io.PrintStream

object KernelStreams {
    var out: PrintStream = System.out
    var err: PrintStream = System.err
}
