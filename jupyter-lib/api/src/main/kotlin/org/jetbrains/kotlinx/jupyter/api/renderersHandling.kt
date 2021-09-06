package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware
import org.jetbrains.kotlinx.jupyter.util.TypeHandlerCodeExecutionSerializer
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError

/**
 * Execution interface for type handlers
 */
fun interface ResultHandlerExecution : VariablesSubstitutionAware<ResultHandlerExecution> {
    fun execute(host: ExecutionHost, result: FieldValue): FieldValue

    override fun replaceVariables(mapping: Map<String, String>): ResultHandlerExecution = this
}

data class FieldValue(val value: Any?, val name: String?)

/**
 * Execution represented by code snippet.
 * This snippet should return the value.
 */
@Serializable(TypeHandlerCodeExecutionSerializer::class)
class ResultHandlerCodeExecution(val code: Code) : ResultHandlerExecution {
    override fun execute(host: ExecutionHost, result: FieldValue): FieldValue {
        val execCode = result.name?.let { code.replace("\$it", it) } ?: code
        return host.execute {
            execute(execCode)
        }
    }

    override fun replaceVariables(mapping: Map<String, String>): ResultHandlerCodeExecution {
        return ResultHandlerCodeExecution(org.jetbrains.kotlinx.jupyter.util.replaceVariables(code, mapping))
    }
}

/**
 * [RendererHandler] renders results for which [accepts] returns `true`
 */
interface RendererHandler : VariablesSubstitutionAware<RendererHandler> {
    /**
     * Returns true if this renderer accepts [value], false otherwise
     */
    fun accepts(value: Any?): Boolean

    /**
     * Execution to handle result.
     * Should not throw if [accepts] returns true
     */
    val execution: ResultHandlerExecution
}

/**
 * [RendererTypeHandler] handles results for which runtime types [acceptsType] returns `true`
 */
interface RendererTypeHandler : RendererHandler {
    fun acceptsType(type: KClass<*>): Boolean

    override fun accepts(value: Any?): Boolean {
        return if (value == null) false else acceptsType(value::class)
    }
}

/**
 * Precompiled renderer type handler. Override this interface if
 * you want type rendering to be optimized.
 */
interface PrecompiledRendererTypeHandler : RendererTypeHandler {
    /**
     * `true` if this type handler may be precompiled
     */
    val mayBePrecompiled: Boolean

    /**
     * Returns method code for rendering
     *
     * @param methodName Precompiled method name
     * @param paramName Name for result value parameter
     * @return Method code if renderer may be precompiled, null otherwise
     */
    fun precompile(methodName: String, paramName: String): Code?
}

/**
 * Simple implementation for [RendererTypeHandler].
 * Renders any type by default
 */
open class AlwaysRendererTypeHandler(override val execution: ResultHandlerExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean = true
    override fun replaceVariables(mapping: Map<String, String>) = this
}

/**
 * Serializable version of type handler.
 * Renders only classes which exactly match [className] by FQN.
 * Accepts only [ResultHandlerCodeExecution] because it's the only one that
 * may be correctly serialized.
 */
@Serializable
class ExactRendererTypeHandler(val className: TypeName, override val execution: ResultHandlerCodeExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean {
        return className == type.java.canonicalName
    }

    override fun replaceVariables(mapping: Map<String, String>): RendererTypeHandler {
        return ExactRendererTypeHandler(className, execution.replaceVariables(mapping))
    }
}

/**
 * Renders any object of [superType] (including subtypes).
 * If [execution] is [ResultHandlerCodeExecution], this renderer may be
 * optimized by pre-compilation (unlike [ExactRendererTypeHandler]).
 */
class SubtypeRendererTypeHandler(private val superType: KClass<*>, override val execution: ResultHandlerExecution) : PrecompiledRendererTypeHandler {
    override val mayBePrecompiled: Boolean
        get() = execution is ResultHandlerCodeExecution

    override fun precompile(methodName: String, paramName: String): Code? {
        if (execution !is ResultHandlerCodeExecution) return null

        val typeParamsString = superType.typeParameters.run {
            if (isEmpty()) {
                ""
            } else {
                joinToString(", ", "<", ">") { "*" }
            }
        }
        val typeDef = superType.qualifiedName!! + typeParamsString
        val methodBody = org.jetbrains.kotlinx.jupyter.util.replaceVariables(execution.code, mapOf("it" to paramName))

        return "fun $methodName($paramName: $typeDef): Any? = $methodBody"
    }

    override fun acceptsType(type: KClass<*>): Boolean {
        return try {
            type.isSubclassOf(superType)
        } catch (e: UnsupportedOperationException) {
            false
        } catch (e: KotlinReflectionInternalError) {
            false
        }
    }

    override fun replaceVariables(mapping: Map<String, String>): SubtypeRendererTypeHandler {
        return SubtypeRendererTypeHandler(superType, execution.replaceVariables(mapping))
    }
}

inline fun <reified T : Any> createRenderer(crossinline renderAction: (T) -> Any?): RendererTypeHandler {
    return SubtypeRendererTypeHandler(T::class) { _, result ->
        FieldValue(renderAction(result.value as T), result.name)
    }
}
