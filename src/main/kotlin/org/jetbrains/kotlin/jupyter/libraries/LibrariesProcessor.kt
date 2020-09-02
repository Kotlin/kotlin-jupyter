package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlin.jupyter.api.LibraryDefinition
import org.jetbrains.kotlin.jupyter.LibraryDescriptor
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.ReplRuntimeProperties
import org.jetbrains.kotlin.jupyter.Variable
import org.jetbrains.kotlin.jupyter.api.LibraryDefinitionProducer
import org.jetbrains.kotlin.jupyter.api.TypeHandler

class LibrariesProcessor(
    private val libraries: LibraryResolver?,
    private val runtimeProperties: ReplRuntimeProperties,
    val libraryFactory: LibraryFactory,
) {

    /**
     * Matches a list of actual library arguments with declared library parameters
     * Arguments can be named or not. Named arguments should be placed after unnamed
     * Parameters may have default value
     *
     * @return A name-to-value map of library arguments
     */
    private fun substituteArguments(parameters: List<Variable>, arguments: List<Variable>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (arguments.any { it.name.isEmpty() }) {
            if (parameters.count() != 1)
                throw ReplCompilerException("Unnamed argument is allowed only if library has a single property")
            if (arguments.count() != 1)
                throw ReplCompilerException("Too many arguments")
            result[parameters[0].name] = arguments[0].value
            return result
        }

        arguments.forEach {
            result[it.name] = it.value
        }
        parameters.forEach {
            if (!result.containsKey(it.name)) {
                result[it.name] = it.value
            }
        }
        return result
    }

    private fun processDescriptor(library: LibraryDescriptor, mapping: Map<String, String>): LibraryDefinitionProducer {
        val definitionCodes = library.libraryDefinitions
        return if (definitionCodes.isEmpty()) {
            TrivialLibraryDefinitionProducer(LibraryDefinition(
                    dependencies = library.dependencies.replaceVariables(mapping),
                    repositories = library.repositories.replaceVariables(mapping),
                    imports = library.imports.replaceVariables(mapping),
                    init = library.init.replaceVariables(mapping),
                    shutdown = library.shutdown.replaceVariables(mapping),
                    initCell = library.initCell.replaceVariables(mapping),
                    renderers = library.renderers.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) },
                    converters = library.converters.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) },
                    annotations = library.annotations.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) }
            ))
        } else {
            val initCodes = library.buildDependenciesInitCode(mapping)?.let { listOf(it) } ?: emptyList()
            ResolvingLibraryDefinitionProducer(initCodes, definitionCodes)
        }
    }

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
        val delim = charArrayOf(',', '(', ')')
        while (true) {
            i = text.indexOfAny(delim, i)
            if (i == -1) {
                val res = text.substring(prev, text.length).trim()
                if (res.isNotEmpty())
                    result.add(res)
                return result
            }
            when (text[i]) {
                ',' -> if (commaDepth == 0) {
                    val res = text.substring(prev, i).trim()
                    if (res.isNotEmpty())
                        result.add(res)
                    prev = i + 1
                }
                '(' -> commaDepth++
                ')' -> commaDepth--
            }
            i++
        }
    }

    private fun checkKernelVersionRequirements(name: String, library: LibraryDescriptor) {
        library.minKernelVersion?.let { minVersionStr ->
            val minVersion = KotlinKernelVersion.from(minVersionStr)
                ?: throw ReplCompilerException("Wrong format of minimal kernel version for library '$name': $minVersionStr")
            runtimeProperties.version?.let { currentVersion ->
                if (currentVersion < minVersion) {
                    throw ReplCompilerException("Library '$name' requires at least $minVersion version of kernel. Current kernel version is $currentVersion. Please update kernel")
                }
            }
        }
    }

    fun processNewLibraries(arg: String) =
        splitLibraryCalls(arg).map {
            val (libRef, vars) = libraryFactory.parseReferenceWithArgs(it)
            val library = libraries?.resolve(libRef)
                ?: throw ReplCompilerException("Unknown library '$libRef'")
            checkKernelVersionRequirements(libRef.toString(), library)

            val mapping = substituteArguments(library.variables, vars)

            processDescriptor(library, mapping)
        }
}
