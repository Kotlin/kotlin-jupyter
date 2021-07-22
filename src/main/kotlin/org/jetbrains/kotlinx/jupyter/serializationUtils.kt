package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedVariablesState
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

typealias FieldDescriptor = Map<String, SerializedVariablesState?>
typealias MutableFieldDescriptor = MutableMap<String, SerializedVariablesState?>
typealias PropertiesData = Collection<KProperty1<out Any, *>>

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

    /**
     * Map of Map of seen objects.
     * First Key: cellId
     * Second Key: actual value
     * Value: serialized VariableState
     */
    private val seenObjectsPerCell: MutableMap<Int, MutableMap<Any, SerializedVariablesState>> = mutableMapOf()

    private var currentSerializeCount: Int = 0

    /**
     * Stores info computed descriptors in a cell
     */
    private val computedDescriptorsPerCell: MutableMap<Int, ProcessedDescriptorsState> = mutableMapOf()


    fun serializeVariables(cellId: Int, variablesState: Map<String, VariableState>): Map<String, SerializedVariablesState> {
        if (seenObjectsPerCell.containsKey(cellId)) {
            seenObjectsPerCell[cellId]!!.clear()
        }
        if (variablesState.isEmpty()) {
            return emptyMap()
        }
        currentSerializeCount = 0
        return variablesState.mapValues { serializeVariableState(cellId, it.key, it.value) }
    }

    fun doIncrementalSerialization(cellId: Int, propertyName: String, serializedVariablesState: SerializedVariablesState): SerializedVariablesState {
        val cellDescriptors = computedDescriptorsPerCell[cellId] ?: return serializedVariablesState
        return updateVariableState(cellId, propertyName, cellDescriptors, serializedVariablesState)
    }

    /**
     * @param evaluatedDescriptorsState - origin variable state to get value from
     * @param serializedVariablesState - current state of recursive state to go further
     */
    private fun updateVariableState(cellId: Int, propertyName: String, evaluatedDescriptorsState: ProcessedDescriptorsState,
                                    serializedVariablesState: SerializedVariablesState): SerializedVariablesState {
        val value = evaluatedDescriptorsState.instancesPerState[serializedVariablesState]
        val propertiesData = evaluatedDescriptorsState.processedSerializedVarsState[serializedVariablesState]
        if (propertiesData == null && value != null && value::class.java.isArray) {
            return serializeVariableState(cellId, propertyName, null, value, false)
        }
        val property = propertiesData?.firstOrNull {
            it.name == propertyName
        } ?: return serializedVariablesState

        return serializeVariableState(cellId, propertyName, property, value, false)
    }


    fun serializeVariableState(cellId: Int, name: String?, variableState: VariableState?, isOverride: Boolean = true): SerializedVariablesState {
        if (variableState == null || name == null) return SerializedVariablesState()
        return serializeVariableState(cellId, name, variableState.property, variableState.value, isOverride)
    }

    fun serializeVariableState(cellId: Int, name: String, property: KProperty<*>?, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, property, value)
        val serializedVersion = processedData.serializedVariablesState

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        // always override?
        if (isOverride) {
            computedDescriptorsPerCell[cellId] = ProcessedDescriptorsState()
        }
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]
        currentCellDescriptors!!.processedSerializedVarsState[serializedVersion] = processedData.propertiesData

        if (value != null) {
            seenObjectsPerCell[cellId]!![value] = serializedVersion
        }
        if (serializedVersion.isContainer) {
            iterateThroughContainerMembers(cellId, value, serializedVersion.fieldDescriptor, currentCellDescriptors.processedSerializedVarsState[serializedVersion])
        }
        return processedData.serializedVariablesState
    }


    private fun iterateThroughContainerMembers(cellId: Int, callInstance: Any?, descriptor: MutableFieldDescriptor, properties: PropertiesData?, currentDepth: Int = 0): Unit {
        if (properties == null || callInstance == null || currentDepth >= serializationStep) return

        val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()

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
            val value = tryGetValueFromProperty(it, callInstance)

            if (!seenObjectsPerCell!!.containsKey(value)) {
                serializedIteration[name] = createSerializeVariableState(name, it, value)
                descriptor[name] = serializedIteration[name]!!.serializedVariablesState
            }
            if (descriptor[name] != null) {
                instancesPerState[descriptor[name]!!] = value
            }

            if (value != null && !seenObjectsPerCell.containsKey(value)) {
                if (descriptor[name] != null) {
                    seenObjectsPerCell[value] = descriptor[name]!!
                }
            }
            currentSerializeCount++
        }

        if (currentSerializeCount > serializationLimit) {
            return
        }

        val isArrayType = checkCreateForPossibleArray(callInstance, descriptor, serializedIteration)

        serializedIteration.forEach {
            val serializedVariablesState = it.value.serializedVariablesState
            val name = it.key
            if (serializedVariablesState.isContainer) {
                val neededCallInstance = when {
                    descriptor[name] != null -> {
                        instancesPerState[descriptor[name]!!]
                    }
                    isArrayType -> {
                        callInstance
                    }
                    else -> { null }
                }
                if (isArrayType) {
                    if (callInstance is List<*>) {
                        callInstance.forEach { arrayElem ->
                            iterateThroughContainerMembers(cellId, arrayElem, serializedVariablesState.fieldDescriptor,
                                it.value.propertiesData, currentDepth + 1)
                        }
                    } else {
                        callInstance as Array<*>
                        callInstance.forEach { arrayElem ->
                            iterateThroughContainerMembers(cellId, arrayElem, serializedVariablesState.fieldDescriptor,
                                it.value.propertiesData, currentDepth + 1)
                        }
                    }

                    return@forEach
                }
                iterateThroughContainerMembers(cellId, neededCallInstance, serializedVariablesState.fieldDescriptor,
                    it.value.propertiesData, currentDepth + 1)
            }
        }
    }


    private fun createSerializeVariableState(name: String, property: KProperty<*>?, value: Any?): ProcessedSerializedVarsState {
        val simpleName = if (property != null) {
            val returnType = property.returnType
            val classifier = returnType.classifier as KClass<*>
            classifier.simpleName
        } else {
            value?.toString()
        }

        // make it exception-safe
        val membersProperties = try {
            if (value != null) value::class.declaredMemberProperties else null
        } catch (e: Throwable) {
            null
        }
        val isContainer = if (membersProperties != null) (membersProperties.size > 1 || value!!::class.java.isArray) else false
        val type =  if (value!= null && value::class.java.isArray) {
            "Array"
        } else if (isContainer && value is List<*>) {
            "SingletonList"
        } else {
            simpleName.toString()
        }

        val serializedVariablesState = SerializedVariablesState(name, type, getProperString(value), isContainer)

        return ProcessedSerializedVarsState(serializedVariablesState, membersProperties)
    }


    private fun tryGetValueFromProperty(property: KProperty1<Any, *>, callInstance: Any): Any? {
        // some fields may be optimized out like array size. Thus, calling it.isAccessible would return error
        val canAccess = try {
            property.isAccessible
            true
        } catch (e: Throwable) {
            false
        }
        if (!canAccess) return null

        val wasAccessible = property.isAccessible
        property.isAccessible = true
        val value = try {
            property.get(callInstance)
        } catch (e: Throwable) {
            null
        }
        property.isAccessible = wasAccessible

        return value
    }

    private fun checkCreateForPossibleArray(callInstance: Any, descriptorStorage: MutableFieldDescriptor, computedDescriptors: MutableMap<String, ProcessedSerializedVarsState>): Boolean {
        // consider arrays and singleton lists
        return if (computedDescriptors.size == 1 && (callInstance::class.java.isArray || callInstance is List<*>)) {
            val name = callInstance.toString()
            computedDescriptors[name] = createSerializeVariableState(name, null, callInstance)
            descriptorStorage[name] = computedDescriptors[name]?.serializedVariablesState
            true
        } else {
            false
        }
    }

}

// TODO: maybe think of considering the depth?
fun getProperString(value: Any?): String {

    fun print(builder: StringBuilder, containerSize:Int, index: Int, value: Any?): Unit {
        if (index != containerSize - 1) {
            builder.append(value, ", ")
        } else {
            builder.append(value)
        }
    }

    value ?: return "null"

    val kClass = value::class
    val isFromJavaArray = kClass.java.isArray
    try {
        if (isFromJavaArray || kClass.isSubclassOf(Array::class)) {
            value as Array<*>
            return buildString {
                val size = value.size
                value.forEachIndexed { index, it ->
                    print(this, size, index, it)
                }
            }
        }
        val isCollection = kClass.isSubclassOf(Collection::class)

        if (isCollection) {
            value as Collection<*>
            return buildString {
                val size = value.size
                value.forEachIndexed { index, it ->
                    print(this, size, index, it)
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
    } catch (e: Throwable) {
        value.toString()
    }

    return value.toString()
}