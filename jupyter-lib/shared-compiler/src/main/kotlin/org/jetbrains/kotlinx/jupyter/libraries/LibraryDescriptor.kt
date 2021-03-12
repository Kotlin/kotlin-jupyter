package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionImpl
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.util.KotlinKernelVersionSerializer
import org.jetbrains.kotlinx.jupyter.util.RenderersSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

@Serializable
class LibraryDescriptor(
    val dependencies: List<String> = emptyList(),
    @Serializable(VariablesSerializer::class)
    @SerialName("properties")
    val variables: List<Variable> = emptyList(),

    val initCell: List<CodeExecution> = emptyList(),

    val imports: List<String> = emptyList(),
    val repositories: List<String> = emptyList(),
    val init: List<CodeExecution> = emptyList(),
    val shutdown: List<CodeExecution> = emptyList(),
    @Serializable(RenderersSerializer::class)
    val renderers: List<ExactRendererTypeHandler> = emptyList(),

    val resources: List<LibraryResource> = emptyList(),

    val link: String? = null,

    val description: String? = null,

    @Serializable(KotlinKernelVersionSerializer::class)
    val minKernelVersion: KotlinKernelVersion? = null,
) {
    fun convertToDefinition(arguments: List<Variable>): LibraryDefinition {
        val mapping = substituteArguments(variables, arguments)
        return processDescriptor(mapping)
    }

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
            if (parameters.count() != 1) {
                throw ReplPreprocessingException("Unnamed argument is allowed only if library has a single property")
            }
            if (arguments.count() != 1) {
                throw ReplPreprocessingException("Too many arguments")
            }
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

    private fun processDescriptor(mapping: Map<String, String>): LibraryDefinition {
        return LibraryDefinitionImpl(
            dependencies = dependencies.replaceVariables(mapping),
            repositories = repositories.replaceVariables(mapping),
            imports = imports.replaceVariables(mapping),
            init = init.replaceVariables(mapping),
            shutdown = shutdown.replaceVariables(mapping),
            initCell = initCell.replaceVariables(mapping),
            renderers = renderers.replaceVariables(mapping),
            resources = resources.replaceVariables(mapping),
            minKernelVersion = minKernelVersion,
            originalDescriptorText = Json.encodeToString(this),
        )
    }
}
