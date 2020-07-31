package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.KotlinKernelVersion
import kotlinx.coroutines.Deferred

class LibrariesProcessor(private val libraries: Deferred<Map<String, LibraryDescriptor>>?) {

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

    private fun processDescriptor(library: LibraryDescriptor, mapping: Map<String, String>) = LibraryDefinition(
            dependencies = library.dependencies.map { replaceVariables(it, mapping) },
            repositories = library.repositories.map { replaceVariables(it, mapping) },
            imports = library.imports.map { replaceVariables(it, mapping) },
            init = library.init.map { replaceVariables(it, mapping) },
            shutdown = library.shutdown.map { replaceVariables(it, mapping) },
            initCell = library.initCell.map { replaceVariables(it, mapping) },
            renderers = library.renderers.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) },
            converters = library.converters.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) },
            annotations = library.annotations.map { TypeHandler(it.className, replaceVariables(it.code, mapping)) }
    )

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

    private fun replaceVariables(str: String, mapping: Map<String, String>) =
            mapping.asSequence().fold(str) { s, template ->
                s.replace("\$${template.key}", template.value)
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
                val (name, vars) = parseLibraryName(it)
                val library = libraries?.awaitBlocking()?.get(name)
                        ?: throw ReplCompilerException("Unknown library '$name'")
                checkKernelVersionRequirements(name, library)

                val mapping = substituteArguments(library.variables, vars)

                processDescriptor(library, mapping)
            }
}