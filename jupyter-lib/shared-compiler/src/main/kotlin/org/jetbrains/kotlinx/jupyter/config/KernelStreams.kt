package org.jetbrains.kotlinx.jupyter.config

import java.io.PrintStream

object KernelStreams {
    var out: PrintStream = System.out
    var err: PrintStream = System.err
}
