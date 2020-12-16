package org.jetbrains.kotlin.jupyter.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.jupyter.util.TypeHandlerCodeExecutionSerializer
import org.jetbrains.kotlin.jupyter.util.replaceVariables
import kotlin.reflect.KClass

fun interface TypeHandlerExecution : VariablesSubstitutionAvailable<TypeHandlerExecution> {
    fun execute(host: KotlinKernelHost, value: Any?, resultFieldName: String?): KotlinKernelHost.Result

    override fun replaceVariables(mapping: Map<String, String>): TypeHandlerExecution = this
}

interface RendererTypeHandler : VariablesSubstitutionAvailable<RendererTypeHandler> {
    fun acceptsType(type: KClass<*>): Boolean

    val execution: TypeHandlerExecution
}

@Serializable(TypeHandlerCodeExecutionSerializer::class)
class TypeHandlerCodeExecution(val code: Code) : TypeHandlerExecution {
    override fun execute(host: KotlinKernelHost, value: Any?, resultFieldName: String?): KotlinKernelHost.Result {
        val execCode = resultFieldName?.let { code.replace("\$it", it) } ?: code
        return host.executeInternal(execCode)
    }

    override fun replaceVariables(mapping: Map<String, String>): TypeHandlerCodeExecution {
        return TypeHandlerCodeExecution(replaceVariables(code, mapping))
    }
}

class AlwaysRendererTypeHandler(override val execution: TypeHandlerExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean = true
    override fun replaceVariables(mapping: Map<String, String>) = this
}

@Serializable
class ExactRendererTypeHandler(val className: TypeName, override val execution: TypeHandlerCodeExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean {
        return className == type.java.canonicalName
    }

    override fun replaceVariables(mapping: Map<String, String>): RendererTypeHandler {
        return ExactRendererTypeHandler(className, execution.replaceVariables(mapping))
    }
}

@Serializable
class GenerativeTypeHandler(val className: TypeName, val code: Code) : VariablesSubstitutionAvailable<GenerativeTypeHandler> {
    override fun replaceVariables(mapping: Map<String, String>): GenerativeTypeHandler {
        return GenerativeTypeHandler(className, replaceVariables(code, mapping))
    }
}
