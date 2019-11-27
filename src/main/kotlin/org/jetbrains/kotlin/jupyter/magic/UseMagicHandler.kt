package org.jetbrains.kotlin.jupyter.magic

import org.jetbrains.kotlin.jupyter.ArtifactVariable
import org.jetbrains.kotlin.jupyter.LibraryDefinition
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.parseLibraryName

class UseMagicHandler(val libraries: Map<String, LibraryDefinition>) : MagicHandler {

    override val keyword: String = "use"

    private fun substituteArguments(parameters: List<ArtifactVariable>, arguments: List<ArtifactVariable>): Map<String, String> {
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
        initCodes.forEach { builder.appendln(it) }
        val code = builder.toString()
        return templates.asSequence().fold(code) { str, template ->
            str.replace("\$${template.key}", template.value)
        }
    }

    override fun process(arg: String?): String {
        if (arg == null) throw ReplCompilerException("Need some arguments for 'use' command")
        val sb = StringBuilder()
        arg.split(',').map { it.trim() }.forEach {
            val (name, vars) = parseLibraryName(it)
            val library = libraries[name] ?: throw ReplCompilerException("Unknown library '$name'")

            // treat single strings in parsed arguments as values, not names
            val arguments = vars.map { if (it.value == null) ArtifactVariable(null, it.name) else it }
            val mapping = substituteArguments(library.variables, arguments)

            val codeToInsert = library.generateCode(mapping)
            sb.append(codeToInsert)
        }
        return sb.toString()
    }

}