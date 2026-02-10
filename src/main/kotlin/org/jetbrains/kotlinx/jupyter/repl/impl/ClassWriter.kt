package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlinx.jupyter.protocol.api.KernelLoggerFactory
import org.jetbrains.kotlinx.jupyter.protocol.api.getLogger
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

/**
 * Kotlin REPL compiler generates compiled classes consisting of
 * compiled in-memory module and some other classes.
 * Spark may need saving them somewhere to send them to the executors,
 * so this class provides writing classes on disk.
 */
class ClassWriter(
    loggerFactory: KernelLoggerFactory,
    _outputDir: String = "",
) {
    private val logger = loggerFactory.getLogger(this::class)

    val outputDir: Path =
        if (_outputDir == "") {
            val tempDir = Files.createTempDirectory("kotlin-jupyter")
            tempDir.toFile().deleteOnExit()
            tempDir.toAbsolutePath()
        } else {
            Paths.get(_outputDir)
        }

    init {
        logger.debug("Created ClassWriter with path <{}>", outputDir)
    }

    fun writeClasses(
        code: SourceCode,
        classes: KJvmCompiledScript,
    ) {
        try {
            val moduleInMemory = classes.getCompiledModule() as KJvmCompiledModuleInMemory
            moduleInMemory.compilerOutputFiles.forEach { (name, bytes) ->
                if (name.contains("class")) {
                    writeClass(bytes, outputDir.resolve(name))
                }
            }
        } catch (e: Throwable) {
            if (e is ClassCastException || e is NullPointerException) {
                logger.info("Compiled line {} has no in-memory modules", code.name)
            } else {
                throw e
            }
        }
    }

    private fun writeClass(
        classBytes: ByteArray,
        path: Path,
    ) {
        try {
            FileOutputStream(path.toAbsolutePath().toString()).use { fos ->
                BufferedOutputStream(fos).use { out ->
                    out.write(classBytes)
                    out.flush()
                }
            }
        } catch (e: IOException) {
            logger.error(e.message)
        }
    }
}
