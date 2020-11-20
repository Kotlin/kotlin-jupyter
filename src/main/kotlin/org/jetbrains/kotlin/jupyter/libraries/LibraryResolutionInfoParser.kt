package org.jetbrains.kotlin.jupyter.libraries

import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.Variable
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.asSuccess

abstract class LibraryResolutionInfoParser(val name: String, private val parameters: List<Parameter>) {
    fun getInfo(args: List<Variable>): LibraryResolutionInfo {
        val map = when (val mapResult = substituteArguments(parameters, args)) {
            is ResultWithDiagnostics.Success -> mapResult.value
            is ResultWithDiagnostics.Failure -> throw ReplCompilerException(mapResult)
        }

        return getInfo(map)
    }

    abstract fun getInfo(args: Map<String, String>): LibraryResolutionInfo

    companion object {
        fun make(name: String, parameters: List<Parameter>, getInfo: (Map<String, String>) -> LibraryResolutionInfo): LibraryResolutionInfoParser {
            return object : LibraryResolutionInfoParser(name, parameters) {
                override fun getInfo(args: Map<String, String>): LibraryResolutionInfo = getInfo(args)
            }
        }

        private fun substituteArguments(parameters: List<Parameter>, arguments: List<Variable>): ResultWithDiagnostics<Map<String, String>> {
            val result = mutableMapOf<String, String>()
            val possibleParamsNames = parameters.map { it.name }.toHashSet()

            var argIndex = 0

            for (arg in arguments) {
                val param = parameters.getOrNull(argIndex)
                    ?: return diagFailure("Too many arguments for library resolution info: ${arguments.size} got, ${parameters.size} allowed")
                if (arg.name.isNotEmpty()) break

                result[param.name] = arg.value
                argIndex++
            }

            for (i in argIndex until arguments.size) {
                val arg = arguments[i]
                if (arg.name.isEmpty()) return diagFailure("Positional arguments in library resolution info shouldn't appear after keyword ones")
                if (arg.name !in possibleParamsNames) return diagFailure("There is no such argument: ${arg.name}")

                result[arg.name] = arg.value
            }

            parameters.forEach {
                if (!result.containsKey(it.name)) {
                    if (it is Parameter.Optional) result[it.name] = it.default
                    else return diagFailure("Parameter ${it.name} is required, but was not specified")
                }
            }

            return result.asSuccess()
        }
    }
}
