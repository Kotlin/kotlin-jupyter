package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.libraries.ExecutionHost
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware
import org.jetbrains.kotlinx.jupyter.util.TypeHandlerCodeExecutionSerializer
import org.jetbrains.kotlinx.jupyter.util.isSubclassOfCatching
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

/**
 * Execution interface for type handlers
 */
fun interface ResultHandlerExecution : VariablesSubstitutionAware<ResultHandlerExecution> {
    fun execute(host: ExecutionHost, result: FieldValue): FieldValue

    override fun replaceVariables(mapping: Map<String, String>): ResultHandlerExecution = this
}

fun interface KTypeProvider {
    fun provideType(): KType
}

data class FieldValue(val value: Any?, val name: String?, private val typeProvider: KTypeProvider?) {
    constructor(value: Any?, name: String?) : this(value, name, null)

    val type by lazy { typeProvider?.provideType() ?: typeOf<Any?>() }
}

data class RendererHandlerWithPriority(
    val renderer: RendererFieldHandler,
    val priority: Int = ProcessingPriority.DEFAULT,
)

/**
 * Execution represented by code snippet.
 * This snippet should return the value.
 */
@Serializable(TypeHandlerCodeExecutionSerializer::class)
class ResultHandlerCodeExecution(val code: Code) : ResultHandlerExecution {
    override fun execute(host: ExecutionHost, result: FieldValue): FieldValue {
        val argTemplate = "\$it"
        val execCode = if (argTemplate in code) {
            val resName = result.name ?: run {
                val newName = "___myRes"
                host.execute {
                    declare(newName to result.value)
                }
                newName
            }
            code.replace(argTemplate, resName)
        } else code

        return host.execute {
            execute(execCode)
        }
    }

    override fun replaceVariables(mapping: Map<String, String>): ResultHandlerCodeExecution {
        return ResultHandlerCodeExecution(org.jetbrains.kotlinx.jupyter.util.replaceVariables(code, mapping))
    }
}

/**
 * [RendererFieldHandler] renders results for which [acceptsField] returns `true`
 */
interface RendererFieldHandler : VariablesSubstitutionAware<RendererFieldHandler> {
    /**
     * Returns true if this renderer accepts [result], false otherwise
     */
    fun acceptsField(result: FieldValue): Boolean

    /**
     * Execution to handle result.
     * Should not throw if [acceptsField] returns true
     */
    val execution: ResultHandlerExecution
}

/**
 * [RendererHandler] renders results for which [accepts] returns `true`
 */
interface RendererHandler : RendererFieldHandler {
    /**
     * Returns true if this renderer accepts [value], false otherwise
     */
    fun accepts(value: Any?): Boolean

    override fun acceptsField(result: FieldValue): Boolean {
        return accepts(result.value)
    }
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

    override fun toString(): String {
        return "Renderer of any type${execution.asTextSuffix()}"
    }
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

    override fun toString(): String {
        return "Exact renderer of $className${execution.asTextSuffix()}"
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
        return type.isSubclassOfCatching(superType)
    }

    override fun replaceVariables(mapping: Map<String, String>): SubtypeRendererTypeHandler {
        val executionCopy = execution.replaceVariables(mapping)
        if (executionCopy === execution) return this
        return SubtypeRendererTypeHandler(superType, executionCopy)
    }

    override fun toString(): String {
        return "Renderer of subtypes of $superType${execution.asTextSuffix()}"
    }
}

inline fun <T : Any> createRenderer(kClass: KClass<T>, crossinline renderAction: (T) -> Any?): RendererFieldHandler {
    return createRenderer({ it.isOfRuntimeType(kClass) }, { field ->
        @Suppress("UNCHECKED_CAST")
        renderAction(field.value as T)
    },)
}

inline fun <reified T : Any> createRenderer(crossinline renderAction: (T) -> Any?): RendererFieldHandler {
    return createRenderer(T::class, renderAction)
}

inline fun <reified T : Any> createRendererByCompileTimeType(crossinline renderAction: (FieldValue) -> Any?): RendererFieldHandler {
    return createRendererByCompileTimeType(typeOf<T>(), renderAction)
}

inline fun createRendererByCompileTimeType(kType: KType, crossinline renderAction: (FieldValue) -> Any?): RendererFieldHandler {
    return createRenderer({ it.isOfCompileTimeType(kType) }, renderAction)
}

inline fun createRenderer(crossinline renderCondition: (FieldValue) -> Boolean, crossinline renderAction: (FieldValue) -> Any?): RendererFieldHandler {
    return createRenderer(renderCondition) { _, field -> renderAction(field) }
}

inline fun createRenderer(crossinline renderCondition: (FieldValue) -> Boolean, crossinline renderAction: (KotlinKernelHost, FieldValue) -> Any?): RendererFieldHandler {
    return object : RendererFieldHandler {
        override fun acceptsField(result: FieldValue): Boolean {
            return renderCondition(result)
        }

        override val execution: ResultHandlerExecution = ResultHandlerExecution { executionHost, result ->
            FieldValue(executionHost.execute { renderAction(this, result) }, null)
        }

        override fun replaceVariables(mapping: Map<String, String>): RendererFieldHandler {
            return this
        }
    }
}

fun FieldValue.isOfRuntimeType(kClass: KClass<*>) = this.value?.let { v -> v::class.isSubclassOfCatching(kClass) } ?: false
fun FieldValue.isOfCompileTimeType(kType: KType) = this.type.isSubtypeOf(kType)

private fun ResultHandlerExecution.asTextSuffix(): String {
    return (this as? ResultHandlerCodeExecution)
        ?.let { " with execution=[$code]" }
        .orEmpty()
}
