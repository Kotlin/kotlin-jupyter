package org.jetbrains.kotlinx.jupyter.test.repl

import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.daemon.client.DaemonCompilerServiceProvider
import org.jetbrains.kotlinx.jupyter.compiler.impl.InProcessCompilerServiceProvider

/**
 * Enum representing different compiler service modes for testing.
 * Used to create concrete test classes that run with different compilation backends.
 */
enum class CompilerServiceType(
    val compilerServiceProvider: CompilerServiceProvider,
) {
    /**
     * In-process compilation mode. Compiler runs in the same JVM process.
     */
    IN_PROCESS(InProcessCompilerServiceProvider()),

    /**
     * Daemon-based compilation mode. Compiler runs in a separate daemon process.
     */
    DAEMON(DaemonCompilerServiceProvider()),
}
