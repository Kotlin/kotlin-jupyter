package org.jetbrains.kotlin.jupyter.repl

import org.slf4j.LoggerFactory
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
class ClassWriter(_outputDir: String = "") {
    val outputDir: Path = if (_outputDir == "") {
        val tempDir = Files.createTempDirectory("kotlin-jupyter")
        tempDir.toFile().deleteOnExit()
        tempDir.toAbsolutePath()
    } else {
        Paths.get(_outputDir)
    }

    init {
        logger.info("Created ClassWriter with path <$outputDir>")
    }

    fun writeClasses(code: SourceCode, classes: KJvmCompiledScript) {
        writeModuleInMemory(code, classes)
    }

    private fun writeModuleInMemory(code: SourceCode, classes: KJvmCompiledScript) {
        try {
            val moduleInMemory = classes.getCompiledModule() as KJvmCompiledModuleInMemory
            moduleInMemory.compilerOutputFiles.forEach { (name, bytes) ->
                if (name.contains("class")) {
                    writeClass(bytes, outputDir.resolve(name))
                }
            }
        } catch (e: ClassCastException) {
            logger.info("Compiled line " + code.name + " has no in-memory modules")
        } catch (e: NullPointerException) {
            logger.info("Compiled line " + code.name + " has no in-memory modules")
        }
    }

    private fun writeClass(classBytes: ByteArray, path: Path) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(ClassWriter::class.java)
    }
}
