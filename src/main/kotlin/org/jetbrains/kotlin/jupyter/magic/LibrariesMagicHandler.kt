package org.jetbrains.kotlin.jupyter.magic

import org.jetbrains.kotlin.jupyter.LibraryDefinition
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.Variable
import org.jetbrains.kotlin.jupyter.parseLibraryName

class LibrariesMagicHandler(val libraries: Map<String, LibraryDefinition>) : MagicHandler {

    override val keyword: String = "use"

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

    private fun LibraryDefinition.generateCode(templates: Map<String, String>): String {
        val builder = StringBuilder()
        artifacts.forEach { builder.appendln("@file:DependsOn(\"$it\")") }
        imports.forEach { builder.appendln("import $it") }
        init.forEach { builder.appendln(it) }
        val code = builder.toString()
        return templates.asSequence().fold(code) { str, template ->
            str.replace("\$${template.key}", template.value)
        }
    }

    /**
     * Split a command argument into a set of library calls
     * Need special processing of ',' to skip call argument delimeters in brackets
     * E.g. "use lib1(3), lib2(2, 5)" should split into "lib1(3)" and "lib(2, 5)", not into "lib1(3)", "lib(2", "5)"
     */
    fun String.splitLibraryCalls(): List<String> {
        var i = 0
        var prev = 0
        var commaDepth = 0
        val result = mutableListOf<String>()
        val delim = charArrayOf(',', '(', ')')
        while (true) {
            i = indexOfAny(delim, i)
            if (i == -1) {
                val res = substring(prev, length).trim()
                if (res.isNotEmpty())
                    result.add(res)
                return result
            }
            when (this[i]) {
                ',' -> if (commaDepth == 0) {
                    val res = substring(prev, i).trim()
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

    override fun process(arg: String?): String {
        if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
        val sb = StringBuilder()
        arg.splitLibraryCalls().forEach {
            val (name, vars) = parseLibraryName(it)
            val library = libraries[name] ?: throw ReplCompilerException("Unknown library '$name'")

            // treat single strings in parsed arguments as values, not names
            val arguments = vars.map { if (it.value == null) Variable(null, it.name) else it }
            val mapping = substituteArguments(library.variables, arguments)

            val codeToInsert = library.generateCode(mapping)
            sb.append(codeToInsert)
            addedLibraries.add(library)
        }
        return sb.toString()
    }

}