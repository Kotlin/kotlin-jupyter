package org.jetbrains.kotlinx.jupyter.test.protocol

import org.jetbrains.kotlinx.jupyter.kernelServer
import org.jetbrains.kotlinx.jupyter.libraries.getDefaultClasspathResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.repl.ReplConfig
import org.jetbrains.kotlinx.jupyter.repl.config.DefaultReplSettings
import org.jetbrains.kotlinx.jupyter.startup.KernelConfig
import org.jetbrains.kotlinx.jupyter.startup.javaCmdLine
import org.junit.jupiter.api.TestInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.concurrent.thread

interface ServerTestExecutor {
    fun setUp(testInfo: TestInfo, kernelConfig: KernelConfig)
    fun tearDown()
}

class ProcessServerTestExecutor : ServerTestExecutor {
    private lateinit var testLogger: Logger
    private lateinit var fileOut: File
    private lateinit var fileErr: File
    private lateinit var serverProcess: Process

    override fun setUp(testInfo: TestInfo, kernelConfig: KernelConfig) {
        val testName = testInfo.displayName
        val command = kernelConfig.javaCmdLine(javaBin, testName, classpathArg)

        testLogger = LoggerFactory.getLogger("testKernel_$testName")
        fileOut = File.createTempFile("tmp-kernel-out-$testName", ".txt")
        fileErr = File.createTempFile("tmp-kernel-err-$testName", ".txt")

        serverProcess = ProcessBuilder(command)
            .redirectOutput(fileOut)
            .redirectError(fileErr)
            .start()
    }

    override fun tearDown() {
        serverProcess.run {
            destroy()
            waitFor()
        }
        testLogger.apply {
            fileOut.let {
                debug("Kernel output:")
                it.forEachLine { line -> debug(line) }
                it.delete()
            }
            fileErr.let {
                debug("Kernel errors:")
                it.forEachLine { line -> debug(line) }
                it.delete()
            }
        }
    }

    companion object {
        private val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        private val classpathArg = System.getProperty("java.class.path")
    }
}

class ThreadServerTestExecutor : ServerTestExecutor {
    private lateinit var serverThread: Thread

    override fun setUp(testInfo: TestInfo, kernelConfig: KernelConfig) {
        val replConfig = ReplConfig.create(
            getDefaultClasspathResolutionInfoProvider(),
            kernelConfig.homeDir,
        )
        val replSettings = DefaultReplSettings(kernelConfig, replConfig)
        serverThread = thread { kernelServer(replSettings) }
    }

    override fun tearDown() {
        serverThread.interrupt()
    }
}
