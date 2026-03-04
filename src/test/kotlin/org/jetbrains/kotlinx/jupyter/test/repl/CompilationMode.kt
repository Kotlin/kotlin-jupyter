package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.compiler.daemon.DaemonCompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.impl.InProcessCompilerServiceProvider

/**
 * Enum representing different compiler service modes for testing.
 * Used to create concrete test classes that run with different compilation backends.
 *
 * Usage:
 * ```
 * abstract class MyTest(mode: CompilationMode) {
 *     val repl = mode.withMode { createRepl() }
 *     // ... test methods
 * }
 *
 * class MyInProcessTest : MyTest(CompilationMode.IN_PROCESS)
 * class MyDaemonTest : MyTest(CompilationMode.DAEMON)
 * ```
 */
enum class CompilationMode(
    val priorityForInProcessMode: Int,
) {
    /**
     * In-process compilation mode. Compiler runs in the same JVM process.
     */
    IN_PROCESS(DaemonCompilerServiceProvider.PRIORITY + 1),

    /**
     * Daemon-based compilation mode. Compiler runs in a separate daemon process.
     */
    DAEMON(DaemonCompilerServiceProvider.PRIORITY - 1),
    ;

    /**
     * Executes the given block with this compilation mode active for the current thread.
     */
    inline fun <T> withMode(block: () -> T): T = InProcessCompilerServiceProvider.withPriority(priorityForInProcessMode, block)
}
