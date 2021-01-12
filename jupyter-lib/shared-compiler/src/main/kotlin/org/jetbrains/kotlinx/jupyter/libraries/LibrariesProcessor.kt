package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer
import org.jetbrains.kotlinx.jupyter.compiler.util.ReplCompilerException

interface LibrariesProcessor {
    fun processNewLibraries(arg: String): List<LibraryDefinitionProducer>
    val libraryFactory: LibraryFactory
}

class LibrariesProcessorImpl(
    private val libraries: LibraryResolver?,
    private val kernelVersion: KotlinKernelVersion?,
    override val libraryFactory: LibraryFactory,
) : LibrariesProcessor {

    /**
     * Split a command argument into a set of library calls
     * Need special processing of ',' to skip call argument delimiters in brackets
     * E.g. "use lib1(3), lib2(2, 5)" should split into "lib1(3)" and "lib(2, 5)", not into "lib1(3)", "lib(2", "5)"
     */
    private fun splitLibraryCalls(text: String): List<String> {
        var i = 0
        var prev = 0
        var commaDepth = 0
        val result = mutableListOf<String>()
        val delimiters = charArrayOf(',', '(', ')')
        while (true) {
            i = text.indexOfAny(delimiters, i)
            if (i == -1) {
                val res = text.substring(prev, text.length).trim()
                if (res.isNotEmpty()) {
                    result.add(res)
                }
                return result
            }
            when (text[i]) {
                ',' -> if (commaDepth == 0) {
                    val res = text.substring(prev, i).trim()
                    if (res.isNotEmpty()) {
                        result.add(res)
                    }
                    prev = i + 1
                }
                '(' -> commaDepth++
                ')' -> commaDepth--
            }
            i++
        }
    }

    private fun checkKernelVersionRequirements(name: String, library: LibraryDefinition) {
        library.minKernelVersion?.let { minVersion ->
            kernelVersion?.let { currentVersion ->
                if (currentVersion < minVersion) {
                    throw ReplCompilerException("Library '$name' requires at least $minVersion version of kernel. Current kernel version is $currentVersion. Please update kernel")
                }
            }
        }
    }

    override fun processNewLibraries(arg: String): List<LibraryDefinitionProducer> =
        splitLibraryCalls(arg).map {
            val (libRef, vars) = libraryFactory.parseReferenceWithArgs(it)
            val library = libraries?.resolve(libRef, vars)
                ?: throw ReplCompilerException("Unknown library '$libRef'")

            checkKernelVersionRequirements(libRef.toString(), library)

            TrivialLibraryDefinitionProducer(library)
        }
}
