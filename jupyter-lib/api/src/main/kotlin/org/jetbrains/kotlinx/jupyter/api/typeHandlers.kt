package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware
import org.jetbrains.kotlinx.jupyter.util.TypeHandlerCodeExecutionSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

fun interface TypeHandlerExecution : VariablesSubstitutionAware<TypeHandlerExecution> {
    fun execute(host: KotlinKernelHost, value: Any?, resultFieldName: String?): KotlinKernelHost.Result

    override fun replaceVariables(mapping: Map<String, String>): TypeHandlerExecution = this
}

interface RendererTypeHandler : VariablesSubstitutionAware<RendererTypeHandler> {
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

class SubtypeRendererTypeHandler(private val superType: KClass<*>, override val execution: TypeHandlerExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean {
        return type.isSubclassOf(superType)
    }

    override fun replaceVariables(mapping: Map<String, String>): SubtypeRendererTypeHandler {
        return SubtypeRendererTypeHandler(superType, execution.replaceVariables(mapping))
    }
}

@Serializable
class GenerativeTypeHandler(val className: TypeName, val code: Code) : VariablesSubstitutionAware<GenerativeTypeHandler> {
    override fun replaceVariables(mapping: Map<String, String>): GenerativeTypeHandler {
        return GenerativeTypeHandler(className, replaceVariables(code, mapping))
    }
}
