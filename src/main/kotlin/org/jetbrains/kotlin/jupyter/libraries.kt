package org.jetbrains.kotlin.jupyter

class LibrariesProcessor {

    private val addedLibraries = mutableListOf<LibraryDefinition>()

    fun popAddedLibraries(): List<LibraryDefinition> {
        val result = addedLibraries.toList()
        addedLibraries.clear()
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

    private fun generateCode(repl: ReplForJupyter, library: LibraryDefinition, templates: Map<String, String>): String {
        val builder = StringBuilder()
        library.repositories.forEach { builder.appendln("@file:Repository(\"$it\")") }
        library.artifacts.forEach { builder.appendln("@file:DependsOn(\"$it\")") }
        library.imports.forEach { builder.appendln("import $it") }
        library.init.forEach { builder.appendln(repl.preprocessCode(it)) }
        val code = builder.toString()
        return templates.asSequence().fold(code) { str, template ->
            str.replace("\$${template.key}", template.value)
        }
    }

    fun generateCodeForLibrary(repl: ReplForJupyter, call: String): String {
        val (name, vars) = parseLibraryName(call)
        val library = repl.config?.libraries?.get(name) ?: throw ReplCompilerException("Unknown library '$name'")

        // treat single strings in parsed arguments as values, not names
        val arguments = vars.map { if (it.value == null) Variable(null, it.name) else it }
        val mapping = substituteArguments(library.variables, arguments)

        addedLibraries.add(library)

        return generateCode(repl, library, mapping)
    }
}