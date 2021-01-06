package org.jetbrains.kotlinx.jupyter.api

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.libraries.VariablesSubstitutionAware
import org.jetbrains.kotlinx.jupyter.util.TypeHandlerCodeExecutionSerializer
import org.jetbrains.kotlinx.jupyter.util.replaceVariables
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * Execution interface for type handlers
 */
fun interface TypeHandlerExecution : VariablesSubstitutionAware<TypeHandlerExecution> {
    fun execute(host: KotlinKernelHost, value: Any?, resultFieldName: String?): KotlinKernelHost.Result

    override fun replaceVariables(mapping: Map<String, String>): TypeHandlerExecution = this
}

/**
 * Type handler for result renderers
 */
interface RendererTypeHandler : VariablesSubstitutionAware<RendererTypeHandler> {
    /**
     * Returns true if this renderer accepts [type], false otherwise
     */
    fun acceptsType(type: KClass<*>): Boolean

    /**
     * Execution to handle result.
     * Should not throw if [acceptsType] returns true
     */
    val execution: TypeHandlerExecution
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
 * Execution represented by code snippet.
 * This snippet should return the value.
 */
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

/**
 * Simple implementation for [RendererTypeHandler].
 * Renders any type by default
 */
open class AlwaysRendererTypeHandler(override val execution: TypeHandlerExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean = true
    override fun replaceVariables(mapping: Map<String, String>) = this
}

/**
 * Serializable version of type handler.
 * Renders only classes which exactly match [className] by FQN.
 * Accepts only [TypeHandlerCodeExecution] because it's the only one that
 * may be correctly serialized.
 */
@Serializable
class ExactRendererTypeHandler(val className: TypeName, override val execution: TypeHandlerCodeExecution) : RendererTypeHandler {
    override fun acceptsType(type: KClass<*>): Boolean {
        return className == type.java.canonicalName
    }

    override fun replaceVariables(mapping: Map<String, String>): RendererTypeHandler {
        return ExactRendererTypeHandler(className, execution.replaceVariables(mapping))
    }
}

/**
 * Renders any object of [superType] (including subtypes).
 * If [execution] is [TypeHandlerCodeExecution], this renderer may be
 * optimized by pre-compilation (unlike [ExactRendererTypeHandler]).
 */
class SubtypeRendererTypeHandler(private val superType: KClass<*>, override val execution: TypeHandlerExecution) : PrecompiledRendererTypeHandler {
    override val mayBePrecompiled: Boolean
        get() = execution is TypeHandlerCodeExecution

    override fun precompile(methodName: String, paramName: String): Code? {
        if (execution !is TypeHandlerCodeExecution) return null

        val typeParamsString = superType.typeParameters.run {
            if (isEmpty()) {
                ""
            } else {
                joinToString(", ", "<", ">") { "*" }
            }
        }
        val typeDef = superType.qualifiedName!! + typeParamsString
        val methodBody = replaceVariables(execution.code, mapOf("it" to paramName))

        return "fun $methodName($paramName: $typeDef): Any? = $methodBody"
    }

    override fun acceptsType(type: KClass<*>): Boolean {
        return type.isSubclassOf(superType)
    }

    override fun replaceVariables(mapping: Map<String, String>): SubtypeRendererTypeHandler {
        return SubtypeRendererTypeHandler(superType, execution.replaceVariables(mapping))
    }
}

/**
 * Generative type handler is used for processing type converters and annotations
 *
 * @see [org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition.annotations]
 * @see [org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition.converters]
 */
@Serializable
class GenerativeTypeHandler(val className: TypeName, val code: Code) : VariablesSubstitutionAware<GenerativeTypeHandler> {
    override fun replaceVariables(mapping: Map<String, String>): GenerativeTypeHandler {
        return GenerativeTypeHandler(className, replaceVariables(code, mapping))
    }
}

/**
 * Callback to handle new class or interface declarations in executed snippets
 */
typealias ClassDeclarationsCallback = (List<KClass<*>>, KotlinKernelHost) -> Unit

/**
 * Annotation handler used to hook class declarations with specific annotations
 */
class AnnotationHandler(val annotation: KClass<out Annotation>, val callback: ClassDeclarationsCallback)
