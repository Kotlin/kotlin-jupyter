package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.jupyter.api.ExactRendererTypeHandler
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.DescriptorVariables
import org.jetbrains.kotlinx.jupyter.api.libraries.DescriptorVariablesSerializer
import org.jetbrains.kotlinx.jupyter.api.libraries.KernelRepository
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.Variable
import org.jetbrains.kotlinx.jupyter.api.libraries.filter
import org.jetbrains.kotlinx.jupyter.api.libraries.libraryDefinition
import org.jetbrains.kotlinx.jupyter.config.currentKernelVersion
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException
import org.jetbrains.kotlinx.jupyter.util.KotlinKernelVersionSerializer
import org.jetbrains.kotlinx.jupyter.util.PatternNameAcceptanceRule
import org.jetbrains.kotlinx.jupyter.util.RenderersSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

@Serializable
data class LibraryDescriptor(
    val dependencies: List<String> = emptyList(),
    @Serializable(DescriptorVariablesSerializer::class)
    @SerialName("properties")
    val variables: DescriptorVariables = DescriptorVariables(),

    val initCell: List<CodeExecution> = emptyList(),

    val imports: List<String> = emptyList(),
    val repositories: List<KernelRepository> = emptyList(),
    val init: List<CodeExecution> = emptyList(),
    val shutdown: List<CodeExecution> = emptyList(),
    @Serializable(RenderersSerializer::class)
    val renderers: List<ExactRendererTypeHandler> = emptyList(),

    val resources: List<LibraryResource> = emptyList(),

    val link: String? = null,

    val description: String? = null,

    @Serializable(KotlinKernelVersionSerializer::class)
    val minKernelVersion: KotlinKernelVersion? = null,

    val formatVersion: LibraryDescriptorFormatVersion? = null,

    val integrationTypeNameRules: List<PatternNameAcceptanceRule> = emptyList(),
) {
    fun convertToDefinition(
        arguments: List<Variable>,
        options: LibraryDescriptorGlobalOptions = DefaultLibraryDescriptorGlobalOptions,
    ): LibraryDefinition {
        assertDescriptorFormatVersionCompatible(this)
        val newVariables = variables.filter { !options.isPropertyIgnored(it.name) }
        val descriptor = if (variables != newVariables) this.copy(variables = newVariables) else this

        val mapping = substituteArguments(descriptor.variables, arguments)
        return descriptor.processDescriptor(mapping)
    }

    /**
     * Matches a list of actual library arguments with declared library parameters
     * Arguments can be named or not. Named arguments should be placed after unnamed
     * Parameters may have default value
     *
     * @return A name-to-value map of library arguments
     */
    private fun substituteArguments(descriptorVariables: DescriptorVariables, arguments: List<Variable>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        fun addResult(name: String, value: String) {
            result[name] = substituteKernelVars(value)
        }

        val parameters = descriptorVariables.properties

        if (descriptorVariables.hasOrder) {
            var namedPart = false
            for ((i, arg) in arguments.withIndex()) {
                val isNamed = arg.name.isNotEmpty()
                if (namedPart) {
                    if (!isNamed) throw ReplPreprocessingException("Unnamed argument is not allowed after named one")
                } else {
                    if (isNamed) namedPart = true
                }

                assert(namedPart == isNamed) { "Named argument might be only in the named part of call" }

                if (isNamed) {
                    addResult(arg.name, arg.value)
                } else {
                    if (parameters.lastIndex < i) throw ReplPreprocessingException("Number of unnamed arguments cannot be more than the number of parameters")
                    addResult(parameters[i].name, arg.value)
                }
            }
        } else {
            if (arguments.any { it.name.isEmpty() }) {
                if (parameters.count() != 1) {
                    throw ReplPreprocessingException("Unnamed argument is allowed only if library has a single property")
                }
                if (arguments.count() != 1) {
                    throw ReplPreprocessingException("Too many unnamed arguments")
                }
                addResult(parameters[0].name, arguments[0].value)
                return result
            }

            arguments.forEach {
                addResult(it.name, it.value)
            }
        }

        parameters.forEach {
            if (!result.containsKey(it.name)) {
                addResult(it.name, it.value)
            }
        }
        return result
    }

    private fun processDescriptor(mapping: Map<String, String>): LibraryDefinition {
        return libraryDefinition {
            it.options = mapping
            it.description = description
            it.website = link
            it.dependencies = dependencies.replaceVariables(mapping)
            it.repositories = repositories.replaceVariables(mapping)
            it.imports = imports.replaceVariables(mapping)
            it.init = init.replaceVariables(mapping)
            it.shutdown = shutdown.replaceVariables(mapping)
            it.initCell = initCell.replaceVariables(mapping)
            it.renderers = renderers.replaceVariables(mapping)
            it.resources = resources.replaceVariables(mapping)
            it.minKernelVersion = minKernelVersion
            it.integrationTypeNameRules = integrationTypeNameRules.replaceVariables(mapping)
            it.originalDescriptorText = Json.encodeToString(this)
        }
    }

    companion object {
        private val kernelVariables = mapOf(
            "kernelVersion" to currentKernelVersion.toString(),
            "kernelMavenVersion" to currentKernelVersion.toMavenVersion(),
        )

        fun substituteKernelVars(value: String): String {
            return replaceVariables(value, kernelVariables)
        }
    }
}
