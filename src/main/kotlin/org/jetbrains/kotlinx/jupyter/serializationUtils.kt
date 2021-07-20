package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedVariablesState
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

typealias FieldDescriptor = Map<String, SerializedVariablesState?>
typealias MutableFieldDescriptor = MutableMap<String, SerializedVariablesState?>
typealias PropertiesData =  Collection<KProperty1<out Any, *>>

data class ProcessedSerializedVarsState(
    val serializedVariablesState: SerializedVariablesState,
    val propertiesData: PropertiesData?
)

data class ProcessedDescriptorsState(
    // perhaps, better tp make SerializedVariablesState -> PropertiesData?
    val processedSerializedVarsState: MutableMap<SerializedVariablesState, PropertiesData?> = mutableMapOf(),
    val instancesPerState: MutableMap<SerializedVariablesState, Any?> = mutableMapOf()
)

class VariablesSerializer(private val serializationStep: Int = 2, private val serializationLimit: Int = 10000) {

    private val seenObjectsPerCell: MutableMap<Int, MutableMap<Any, SerializedVariablesState>> = mutableMapOf()

    var currentSerializeCount: Int = 0

    /**
     * Stores info computed descriptors in a cell
     */
    private val computedDescriptorsPerCell: MutableMap<Int, ProcessedDescriptorsState> = mutableMapOf()


    fun serializeVariables(cellId: Int, variablesState: Map<String, VariableState>): Map<String, SerializedVariablesState> {
       return variablesState.mapValues { serializeVariableState(cellId, it.key, it.value) }
    }

    fun doIncrementalSerialization(cellId: Int, propertyName: String, serializedVariablesState: SerializedVariablesState): SerializedVariablesState {
        val cellDescriptors = computedDescriptorsPerCell[cellId] ?: return serializedVariablesState
        return updateVariableState(propertyName, cellDescriptors, serializedVariablesState)
    }

    /**
     * @param evaluatedDescriptorsState - origin variable state to get value from
     * @param serializedVariablesState - current state of recursive state to go further
     */
    private fun updateVariableState(propertyName: String, evaluatedDescriptorsState: ProcessedDescriptorsState,
                            serializedVariablesState: SerializedVariablesState) : SerializedVariablesState {
        val value = evaluatedDescriptorsState.instancesPerState[serializedVariablesState]
        val propertiesData = evaluatedDescriptorsState.processedSerializedVarsState[serializedVariablesState] ?: return serializedVariablesState
        val property = propertiesData.firstOrNull {
            it.name == propertyName
        } ?: return serializedVariablesState

        return serializeVariableState(propertyName, property, value)
    }


    fun serializeVariableState(cellId: Int, name: String, variableState: VariableState): SerializedVariablesState {
        return serializeVariableState(cellId, name, variableState.property, variableState.value)
    }

    fun serializeVariableState(cellId: Int, name: String, property: KProperty<*>, value: Any?): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, property, value)
        val serializedVersion = processedData.serializedVariablesState

        if (seenObjectsPerCell.containsKey(cellId)) {
            seenObjectsPerCell[cellId]!!.clear()
        }
        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        // always override?
        computedDescriptorsPerCell[cellId] = ProcessedDescriptorsState()
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]
        currentCellDescriptors!!.processedSerializedVarsState[serializedVersion] = processedData.propertiesData

        if (value != null) {
            seenObjectsPerCell[cellId]!![value] = serializedVersion
        }
        if (serializedVersion.isContainer) {
            iterateThroughContainerMembers(cellId, value, serializedVersion.fieldDescriptor, processedData.propertiesData)
        }
        return processedData.serializedVariablesState
    }


    private fun iterateThroughContainerMembers(cellId: Int, callInstance: Any?, descriptor: MutableFieldDescriptor, properties: PropertiesData?, currentDepth: Int = 0): Unit {
        if (properties == null || callInstance == null || currentDepth > serializationStep) return

        val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()
        val callInstances = mutableMapOf<String, Any?>()

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        val seenObjectsPerCell = seenObjectsPerCell[cellId]
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]!!
        val instancesPerState = currentCellDescriptors.instancesPerState

        for (it in properties) {
            if (currentSerializeCount > serializationLimit) {
                break
            }
            it as KProperty1<Any, *>
            val name = it.name
            val wasAccessible = it.isAccessible
            it.isAccessible = true
            val value = it.get(callInstance)

            if (!seenObjectsPerCell!!.containsKey(value)) {
                serializedIteration[name] = createSerializeVariableState(name, it, value)
                descriptor[name] = serializedIteration[name]!!.serializedVariablesState
            }
            instancesPerState[descriptor[name]!!] = value

            if (value != null && !seenObjectsPerCell.containsKey(value)) {
                if (descriptor[name] != null) {
                    seenObjectsPerCell[value] = descriptor[name]!!
                }
            }
            it.isAccessible = wasAccessible
            currentSerializeCount++
        }

        serializedIteration.forEach {
            val serializedVariablesState = it.value.serializedVariablesState
            val name = it.key
            if (serializedVariablesState.isContainer) {
                iterateThroughContainerMembers(cellId, callInstances[name], serializedVariablesState.fieldDescriptor,
                    it.value.propertiesData, currentDepth + 1)
            }
        }
    }


}
// TODO: place code bellow to the VariablesSerializer once it's good
/**
 * Map of seen objects.
 * Key: hash code of actual value
 * Value:  this value
 */
val seenObjects: MutableMap<Any, SerializedVariablesState> = mutableMapOf()
var currentSerializeCount: Int = 0
val computedDescriptorsPerCell: Map<Int, ProcessedDescriptorsState> = mutableMapOf()


fun serializeVariableState(name: String, property: KProperty<*>, value: Any?): SerializedVariablesState {
    val processedData = createSerializeVariableState(name, property, value)
    val serializedVersion = processedData.serializedVariablesState
    if (value != null) {
        seenObjects[value] = serializedVersion
    }
    if (serializedVersion.isContainer) {
        iterateThroughContainerMembers(value, serializedVersion.fieldDescriptor, processedData.propertiesData)
    }
    return processedData.serializedVariablesState
}

fun serializeVariableState(name: String, variableState: VariableState): SerializedVariablesState {
    return serializeVariableState(name, variableState.property, variableState.value)
}

// maybe let it be global
fun createSerializeVariableState(name: String, property: KProperty<*>, value: Any?): ProcessedSerializedVarsState {
    val returnType = property.returnType
    val classifier = returnType.classifier as KClass<*>
    val membersProperties = if (value != null) value::class.declaredMemberProperties else null
    val isContainer = if (membersProperties != null) membersProperties.size > 1 else false
    val serializedVariablesState = SerializedVariablesState(name, classifier.simpleName.toString(),
                                                                                getProperString(value), isContainer)

    return ProcessedSerializedVarsState(serializedVariablesState, membersProperties)
}

fun createSerializeVariableState(name: String, variableState: VariableState): ProcessedSerializedVarsState {
    val returnType = variableState.property.returnType
    val classifier = returnType.classifier as KClass<*>
    val property = variableState.property
    val javaField = property.returnType.jvmErasure

    val membersProperties = if (variableState.value != null) variableState.value!!::class.declaredMemberProperties else null
    val isContainer = if (membersProperties != null) membersProperties.size > 1 else false
    val serializedVariablesState = SerializedVariablesState(name, classifier.simpleName.toString(), variableState.stringValue, isContainer)

    return ProcessedSerializedVarsState(serializedVariablesState, membersProperties)
}

fun iterateThroughContainerMembers(callInstance: Any?, descriptor: MutableFieldDescriptor, properties: PropertiesData?, currentDepth: Int = 0): Unit {
    if (properties == null || callInstance == null || currentDepth > 2) return

    val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()
    val callInstances = mutableMapOf<String, Any?>()

    for (it in properties) {
        if (currentSerializeCount > 1000) {
            break
        }
        it as KProperty1<Any, *>
        val name = it.name
        val wasAccessible = it.isAccessible
        it.isAccessible = true
        val value = it.get(callInstance)

        if (!seenObjects.containsKey(value)) {
            serializedIteration[name] = createSerializeVariableState(name, it, value)
            descriptor[name] = serializedIteration[name]?.serializedVariablesState
        }

        if (value != null && !seenObjects.containsKey(value)) {
            if (descriptor[name] != null) {
                seenObjects[value] = descriptor[name]!!
            }
        }
        it.isAccessible = wasAccessible
        currentSerializeCount++
    }

    serializedIteration.forEach {
        val serializedVariablesState = it.value.serializedVariablesState
        val name = it.key
        if (serializedVariablesState.isContainer) {
            iterateThroughContainerMembers(callInstances[name], serializedVariablesState.fieldDescriptor,
                        it.value.propertiesData, currentDepth + 1)
        }
    }
}


fun getProperString(value: Any?) : String {
    value ?: return "null"

    val kClass = value::class
    val isFromJavaArray = kClass.java.isArray
    if (isFromJavaArray || kClass.isSubclassOf(Array::class)) {
        value as Array<*>
        return value.toString()
    }
    val isCollection = kClass.isSubclassOf(Collection::class)
    if (isCollection) {
        value as Collection<*>
        return buildString {
            value.forEach {
                append(it.toString(), ", ")
            }
        }
    }
    val isMap = kClass.isSubclassOf(Map::class)
    if (isMap) {
        value as Map<Any, Any?>
        return buildString {
            value.forEach {
                append(it.key, '=', it.value, "\n")
            }
        }
    }
    return value.toString()
}