package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.completion.KotlinFunctionInfo
import org.jetbrains.kotlin.jupyter.repl.reflect.ContextUpdater
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.isAccessible

interface TypeProvidersProcessor {

    fun register(handler: TypeHandler): Code

    fun process(): List<Code>
}

class TypeProvidersProcessorImpl(private val contextUpdater: ContextUpdater) : TypeProvidersProcessor {

    private val handlers = mutableMapOf<Regex, KotlinFunctionInfo>()

    private val methodIdMap = mutableMapOf<TypeName, Int>()

    private val codeToMethodMap = mutableMapOf<Code, Int>()

    private var nextGeneratedMethodId = 0

    private class TypeConverterCodes(val declarations: List<Code>, val converter: Code, val wildcard: String? = "###") {

        constructor(code: List<Code>) : this(code.subList(0, code.size - 1), code.last())

        val hasWildcard = wildcard != null && (declarations.any { it.contains(wildcard) } || converter.contains(wildcard))

        val fullCode: String = declarations.joinToString("\n") + "\n" + converter

        override fun toString(): String {
            return fullCode
        }

        fun withoutDeclarations() = TypeConverterCodes(emptyList(), converter, wildcard)

        fun replaceWildcard(str: String) = if (!hasWildcard) throw Exception() else
            TypeConverterCodes(declarations.map { it.replace(wildcard!!, str) }, converter.replace(wildcard!!, str), null)
    }

    private fun getMethodName(id: Int) = "___getConverter$id"

    override fun register(handler: TypeHandler): Code {
        val instanceArg = "__it"
        val propertyArg = "__property"
        val body = handler.code.replace("\$it", instanceArg).replace("\$property", propertyArg)
        val type = handler.className
        val methodId = nextGeneratedMethodId++
        val methodName = getMethodName(methodId)
        methodIdMap[type] = methodId
        return "fun $methodName($instanceArg : $type, $propertyArg : kotlin.reflect.KProperty<*>) = $body"
    }

    private fun toRegex(name: TypeName) = name.split('.').joinToString("\\.").split('*').joinToString(".*").toRegex()

    override fun process(): List<Code> {

        if (methodIdMap.isNotEmpty()) {
            contextUpdater.update()
            handlers.putAll(methodIdMap.map {
                toRegex(it.key) to contextUpdater.context.functions[getMethodName(it.value)]!!
            })
            methodIdMap.clear()
        }

        val conversionCodes = mutableMapOf<KProperty<*>, Code>()
        val initCodes = mutableListOf<Code>()
        val variablePlaceholder = "\$it"
        val tempFieldPrefix = "___"

        for (it in contextUpdater.context.getVarsList().filter { !it.name.startsWith(tempFieldPrefix) }) {
            val property = it.descriptor
            property.isAccessible = true
            val value = it.value ?: continue
            val propertyType = property.returnType
            val notNullType = propertyType.withNullability(false)
            val functionInfo = handlers.asIterable().firstOrNull { it.key.matches(notNullType.toString()) }?.value
            if (functionInfo != null) {
                val codes = functionInfo.function.call(functionInfo.line, value, property)?.let { it as? List<String> }
                if (codes != null && codes.isNotEmpty()) {
                    var typeConverterCodes = TypeConverterCodes(codes)
                    if (typeConverterCodes.hasWildcard) {
                        val fullCode = typeConverterCodes.fullCode
                        var id = codeToMethodMap[fullCode]
                        if (id != null) {
                            typeConverterCodes = typeConverterCodes.withoutDeclarations()
                        } else {
                            id = codeToMethodMap.size
                            codeToMethodMap[fullCode] = id
                        }
                        if (typeConverterCodes.hasWildcard)
                            typeConverterCodes = typeConverterCodes.replaceWildcard("$id")
                    }
                    initCodes.addAll(typeConverterCodes.declarations)
                    var converterCode = typeConverterCodes.converter
                    if (propertyType.isMarkedNullable)
                        converterCode = converterCode.replace(variablePlaceholder, "$variablePlaceholder!!")
                    conversionCodes[property] = converterCode
                    continue
                }
            }
            if (propertyType.isMarkedNullable) {
                conversionCodes[property] = "$variablePlaceholder!!"
            }
        }

        if (!conversionCodes.isEmpty()) {
            val initCode = initCodes.joinToLines()
            val tempFieldsCode = conversionCodes
                    .map { "val $tempFieldPrefix${it.key.name} = ${it.key.name}" }
                    .joinToLines()

            val newFieldsCode = conversionCodes
                    .mapValues { it.value.replace(variablePlaceholder, "$tempFieldPrefix${it.key.name}") }
                    .map {
                        val valOrVar = if (it.key is KMutableProperty) "var" else "val"
                        "$valOrVar ${it.key.name} = ${it.value}"
                    }
                    .joinToLines()

            return listOf("$initCode\n$tempFieldsCode", newFieldsCode)
        }
        return emptyList()
    }
}