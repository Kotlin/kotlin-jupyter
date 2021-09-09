package org.jetbrains.kotlinx.jupyter.compiler.util

import kotlinx.serialization.Serializable

typealias Classpath = List<String>

@Serializable
data class SerializedCompiledScript(
    val fileName: String,
    val data: String,
    val isImplicitReceiver: Boolean,
)

@Serializable
data class SerializedCompiledScriptsData(
    val scripts: List<SerializedCompiledScript>
) {
    companion object {
        val EMPTY = SerializedCompiledScriptsData(emptyList())
    }
}

@Serializable
data class SerializableTypeInfo(val type: Type = Type.Custom, val isPrimitive: Boolean = false, val fullType: String = "") {
    companion object {
        val ignoreSet = setOf("int", "double", "boolean", "char", "float", "byte", "string", "entry")

        val propertyNamesForNullFilter = setOf("data", "size")

        fun makeFromSerializedVariablesState(type: String?, isContainer: Boolean?): SerializableTypeInfo {
            val fullType = type.orEmpty()
            val enumType = fullType.toTypeEnum()
            val isPrimitive = !(
                if (fullType != "Entry") (isContainer ?: false)
                else true
                )

            return SerializableTypeInfo(enumType, isPrimitive, fullType)
        }
    }
}

@Serializable
enum class Type {
    Map,
    Entry,
    Array,
    List,
    Custom
}

fun String.toTypeEnum(): Type {
    return when (this) {
        "Map" -> Type.Map
        "Entry" -> Type.Entry
        "Array" -> Type.Array
        "List" -> Type.List
        else -> Type.Custom
    }
}

@Serializable
data class SerializedVariablesState(
    val type: SerializableTypeInfo = SerializableTypeInfo(),
    val value: String? = null,
    val isContainer: Boolean = false,
    val stateId: String = ""
) {
    // todo: not null
    val fieldDescriptor: MutableMap<String, SerializedVariablesState?> = mutableMapOf()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedVariablesState

        if (type != other.type) return false
        if (value != other.value) return false
        if (isContainer != other.isContainer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + isContainer.hashCode()
        return result
    }
}

@Serializable
class SerializationReply(
    val cell_id: Int = 1,
    val descriptorsState: Map<String, SerializedVariablesState> = emptyMap(),
    val comm_id: String = ""
)

@Serializable
class EvaluatedSnippetMetadata(
    val newClasspath: Classpath = emptyList(),
    val compiledData: SerializedCompiledScriptsData = SerializedCompiledScriptsData.EMPTY,
    val newImports: List<String> = emptyList(),
    val evaluatedVariablesState: Map<String, SerializedVariablesState> = emptyMap()
) {
    companion object {
        val EMPTY = EvaluatedSnippetMetadata()
    }
}
