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
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript


/**
 * Kotlin REPL compiler generates compiled classes consisting of
 * compiled in-memory module and some other classes.
 * Spark may need saving them somewhere to send them to the executors,
 * so this class provides writing classes on disk.
 */
class ClassWriter(_outputDir: String = "") {
    var outputDir = _outputDir
        private set

    init {
        if (outputDir == "") {
            val tempDir = Files.createTempDirectory("kotlin-jupyter")
            tempDir.toFile().deleteOnExit()
            outputDir = tempDir.toAbsolutePath().toString()
        }
    }

    fun writeClasses(classes: CompiledClasses) {
        for ((filePath, bytes) in classes.classes) {
            if (!filePath.contains(File.separator)) {
                val classWritePath = outputDir + File.separator + filePath
                writeClass(bytes, classWritePath)
            }
        }
        writeModuleInMemory(classes)
    }

    private fun writeModuleInMemory(classes: CompiledClasses) {
        try {
            val compiledScript: KJvmCompiledScript<*> = classes.data as KJvmCompiledScript<*>
            val moduleInMemory: KJvmCompiledModuleInMemory = compiledScript.compiledModule as KJvmCompiledModuleInMemory
            moduleInMemory.compilerOutputFiles.forEach { (name: String, bytes: ByteArray) ->
                if (name.contains("class")) {
                    writeClass(bytes, outputDir + File.separator + name)
                }
            }
        } catch (e: ClassCastException) {
            logger.info("Compiled line #" + classes.lineId.no + "has no in-memory modules")
        } catch (e: NullPointerException) {
            logger.info("Compiled line #" + classes.lineId.no + "has no in-memory modules")
        }
    }

    private fun writeClass(classBytes: ByteArray, path: String) {
        try {
            FileOutputStream(path).use { fos ->
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
