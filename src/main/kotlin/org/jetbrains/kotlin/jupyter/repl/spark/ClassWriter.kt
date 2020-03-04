package org.jetbrains.kotlin.jupyter.repl.spark

import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult.CompiledClasses
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemory
import org.slf4j.LoggerFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript


/**
 * Kotlin REPL compiler generates compiled classes consisting of
 * compiled in-memory module and some other classes.
 * Spark may need saving them somewhere to send them to the executors,
 * so this class provides writing classes on disk.
 */
class ClassWriter(_outputDir: String = "") {
    val outputDir  = if(_outputDir == "") {
        val tempDir = Files.createTempDirectory("kotlin-jupyter")
        tempDir.toFile().deleteOnExit()
        tempDir.toAbsolutePath()
    } else {
        Paths.get(_outputDir)
    }

    init {
        logger.info("Created ClassWriter with path <$outputDir>")
    }

    fun writeClasses(classes: KJvmCompiledScript<*>) {
        writeModuleInMemory(classes)
    }

    private fun writeModuleInMemory(classes: KJvmCompiledScript<*>) {
        try {
            val moduleInMemory = classes.compiledModule as KJvmCompiledModuleInMemory
            moduleInMemory.compilerOutputFiles.forEach { (name, bytes) ->
                if (name.contains("class")) {
                    writeClass(bytes, outputDir.resolve(name))
                }
            }
        } catch (e: ClassCastException) {
            logger.info("Compiled line " + classes.code.name + " has no in-memory modules")
        } catch (e: NullPointerException) {
            logger.info("Compiled line " + classes.code.name + " has no in-memory modules")
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
