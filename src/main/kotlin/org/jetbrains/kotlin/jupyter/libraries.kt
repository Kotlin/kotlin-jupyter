package org.jetbrains.kotlin.jupyter

class LibrariesProcessor {

    data class LibraryWithCode(val library: LibraryDefinition, val code: String)

    private val processedLibraries = mutableListOf<LibraryWithCode>()

    fun getProcessedLibraries(): List<LibraryWithCode> {
        val result = processedLibraries.toList()
        processedLibraries.clear()
        return result
    }

    /**
     * Matches a list of actual library arguments with declared library parameters
     * Arguments can be named or not. Named arguments should be placed after unnamed
     * Parameters may have default value
     *
     * @return A name-to-value map of library arguments
     */
    private fun substituteArguments(parameters: List<Variable>, arguments: List<Variable>): Map<String, String> {
        val firstNamed = arguments.indexOfFirst { it.name != null }
        if (firstNamed != -1 && arguments.asSequence().drop(firstNamed).any { it.name == null })
            throw ReplCompilerException("Mixing named and positional arguments is not allowed")
        val parameterNames = parameters.map { it.name!! }.toSet()
        val result = mutableMapOf<String, String>()
        for (i in 0 until arguments.count()) {
            if (i >= parameters.count())
                throw ReplCompilerException("Too many arguments")
            val name = arguments[i].name?.also {
                if (!parameterNames.contains(it)) throw ReplCompilerException("Can not find parameter with name '$it'")
            } ?: parameters[i].name!!

            if (result.containsKey(name)) throw ReplCompilerException("An argument for parameter '$name' is already passed")
            result[name] = arguments[i].value!!
        }
        parameters.forEach {
            if (!result.containsKey(it.name!!)) {
                if (it.value == null) throw ReplCompilerException("No value passed for parameter '${it.name}'")
                result[it.name] = it.value
            }
        }
        return result
    }

    private fun generateCode(repl: ReplForJupyter, library: LibraryDefinition, mapping: Map<String, String>): String {
        val builder = StringBuilder()
        library.repositories.forEach { builder.appendln("@file:Repository(\"$it\")") }
        library.artifacts.forEach { builder.appendln("@file:DependsOn(\"$it\")") }
        library.imports.forEach { builder.appendln("import $it") }
        library.init.forEach { builder.appendln(repl.preprocessCode(it)) }
        return builder.toString().replaceVariables(mapping)
    }

    /**
     * Split a command argument into a set of library calls
     * Need special processing of ',' to skip call argument delimeters in brackets
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

    private fun String.replaceVariables(mapping: Map<String, String>) =
            mapping.asSequence().fold(this) { str, template ->
                str.replace("\$${template.key}", template.value)
            }

    fun processNewLibraries(repl: ReplForJupyter, arg: String) {

        splitLibraryCalls(arg).forEach {
            val (name, vars) = parseLibraryName(it)
            val library = repl.config?.libraries?.get(name) ?: throw ReplCompilerException("Unknown library '$name'")

            // treat single strings in parsed arguments as values, not names
            val arguments = vars.map { if (it.value == null) Variable(null, it.name) else it }
            val mapping = substituteArguments(library.variables, arguments)

            processedLibraries.add(LibraryWithCode(library, generateCode(repl, library, mapping)))
        }
    }
}