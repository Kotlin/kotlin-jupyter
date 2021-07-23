package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedVariablesState
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

typealias FieldDescriptor = Map<String, SerializedVariablesState?>
typealias MutableFieldDescriptor = MutableMap<String, SerializedVariablesState?>
typealias PropertiesData = Collection<KProperty1<out Any, *>>

class ProcessedSerializedVarsState(
    val serializedVariablesState: SerializedVariablesState,
    val propertiesData: PropertiesData?,
    val jvmOnlyFields: Array<Field>? = null
)

data class ProcessedDescriptorsState(
    val processedSerializedVarsToKProperties: MutableMap<SerializedVariablesState, PropertiesData?> = mutableMapOf(),
    // do we need this? Probably, not
    // val processedSerializedVarsToJvmFields: MutableMap<SerializedVariablesState, Array<Field>?> = mutableMapOf(),
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

    private val isSerializationActive: Boolean = System.getProperty(serializationEnvProperty)?.toBooleanStrictOrNull() ?: true

    fun serializeVariables(cellId: Int, variablesState: Map<String, VariableState>): Map<String, SerializedVariablesState> {
        if (!isSerializationActive) return emptyMap()

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
        if (!isSerializationActive) return serializedVariablesState

        val cellDescriptors = computedDescriptorsPerCell[cellId] ?: return serializedVariablesState
        return updateVariableState(cellId, propertyName, cellDescriptors, serializedVariablesState)
    }

    /**
     * @param evaluatedDescriptorsState - origin variable state to get value from
     * @param serializedVariablesState - current state of recursive state to go further
     */
    private fun updateVariableState(
        cellId: Int,
        propertyName: String,
        evaluatedDescriptorsState: ProcessedDescriptorsState,
        serializedVariablesState: SerializedVariablesState
    ): SerializedVariablesState {
        val value = evaluatedDescriptorsState.instancesPerState[serializedVariablesState]
        val propertiesData = evaluatedDescriptorsState.processedSerializedVarsToKProperties[serializedVariablesState]
        if (propertiesData == null && value != null && (value::class.java.isArray || value::class.java.isMemberClass)) {
            return serializeVariableState(cellId, propertyName, propertiesData, value, false)
        }
        val property = propertiesData?.firstOrNull {
            it.name == propertyName
        } ?: return serializedVariablesState

        return serializeVariableState(cellId, propertyName, property, value, false)
    }

    fun serializeVariableState(cellId: Int, name: String?, variableState: VariableState?, isOverride: Boolean = true): SerializedVariablesState {
        if (!isSerializationActive || variableState == null || name == null) return SerializedVariablesState()
        return serializeVariableState(cellId, name, variableState.property, variableState.value, isOverride)
    }

    private fun serializeVariableState(cellId: Int, name: String, property: Field?, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, getSimpleTypeNameFrom(property, value), value)
        return doActualSerialization(cellId, processedData, value, isOverride)
    }

    private fun serializeVariableState(cellId: Int, name: String, property: KProperty<*>, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, getSimpleTypeNameFrom(property, value), value)
        return doActualSerialization(cellId, processedData, value, isOverride)
    }

    private fun doActualSerialization(cellId: Int, processedData: ProcessedSerializedVarsState, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        fun isCanBeComputed(fieldDescriptors: MutableMap<String, SerializedVariablesState?>): Boolean {
            return (fieldDescriptors.isEmpty() || (fieldDescriptors.isNotEmpty() && fieldDescriptors.entries.first().value?.fieldDescriptor!!.isEmpty()))
        }

        val serializedVersion = processedData.serializedVariablesState

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        // always override?
        if (isOverride) {
            computedDescriptorsPerCell[cellId] = ProcessedDescriptorsState()
        }
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]
        currentCellDescriptors!!.processedSerializedVarsToKProperties[serializedVersion] = processedData.propertiesData
//        currentCellDescriptors.processedSerializedVarsToJvmFields[serializedVersion] = processedData.jvmOnlyFields

        if (value != null) {
            seenObjectsPerCell[cellId]!!.putIfAbsent(value, serializedVersion)
        }
        if (serializedVersion.isContainer) {
            // check for seen
            if (seenObjectsPerCell[cellId]!!.containsKey(value)) {
                val previouslySerializedState = seenObjectsPerCell[cellId]!![value] ?: return processedData.serializedVariablesState
                serializedVersion.fieldDescriptor += previouslySerializedState.fieldDescriptor
                if (isCanBeComputed(serializedVersion.fieldDescriptor)) {
                    iterateThroughContainerMembers(cellId, value, serializedVersion.fieldDescriptor, currentCellDescriptors.processedSerializedVarsToKProperties[serializedVersion])
                }
            } else {
                // add jvm descriptors
                processedData.jvmOnlyFields?.forEach {
                    serializedVersion.fieldDescriptor[it.name] = serializeVariableState(cellId, it.name, it, value)
                }
                iterateThroughContainerMembers(cellId, value, serializedVersion.fieldDescriptor, currentCellDescriptors.processedSerializedVarsToKProperties[serializedVersion])
            }
        }
        return processedData.serializedVariablesState
    }

    private fun iterateThroughContainerMembers(cellId: Int, callInstance: Any?, descriptor: MutableFieldDescriptor, properties: PropertiesData?, currentDepth: Int = 0) {
        if (properties == null || callInstance == null || currentDepth >= serializationStep) return

        val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        val seenObjectsPerCell = seenObjectsPerCell[cellId]
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]!!
        // ok, it's a copy on the left for some reason
        val instancesPerState = currentCellDescriptors.instancesPerState

        for (it in properties) {
            if (currentSerializeCount > serializationLimit) {
                break
            }
            it as KProperty1<Any, *>
            val name = it.name
            val value = tryGetValueFromProperty(it, callInstance)

            if (!seenObjectsPerCell!!.containsKey(value)) {
                serializedIteration[name] = createSerializeVariableState(name, getSimpleTypeNameFrom(it, value), value)
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
        computedDescriptorsPerCell[cellId]!!.instancesPerState += instancesPerState

        // check for seen
        // for now it's O(c*n)
        if (serializedIteration.isEmpty()) {
            val processedVars = computedDescriptorsPerCell[cellId]!!.processedSerializedVarsToKProperties
            descriptor.forEach { (_, state) ->
                if (processedVars.containsKey(state)) {
                    processedVars.entries.firstOrNull {
                        val itValue = it.key
                        if (itValue.value == state?.value && itValue.type == state?.value) {
                            state?.fieldDescriptor?.put(itValue.type, itValue)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }

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
                    else -> {
                        null
                    }
                }
                if (isArrayType) {
                    if (callInstance is List<*>) {
                        callInstance.forEach { arrayElem ->
                            iterateThroughContainerMembers(
                                cellId,
                                arrayElem,
                                serializedVariablesState.fieldDescriptor,
                                it.value.propertiesData,
                                currentDepth + 1
                            )
                        }
                    } else {
                        callInstance as Array<*>
                        callInstance.forEach { arrayElem ->
                            iterateThroughContainerMembers(
                                cellId,
                                arrayElem,
                                serializedVariablesState.fieldDescriptor,
                                it.value.propertiesData,
                                currentDepth + 1
                            )
                        }
                    }

                    return@forEach
                }

                // update state with JVMFields
                it.value.jvmOnlyFields?.forEach { field ->
                    serializedVariablesState.fieldDescriptor[field.name] = serializeVariableState(cellId, field.name, field, neededCallInstance)
                    val properInstance = serializedVariablesState.fieldDescriptor[field.name]
                    instancesPerState[properInstance!!] = neededCallInstance
                    seenObjectsPerCell?.set(neededCallInstance!!, serializedVariablesState)
                }
                computedDescriptorsPerCell[cellId]!!.instancesPerState += instancesPerState
//                computedDescriptorsPerCell[cellId]!!.processedSerializedVarsToJvmFields[serializedVariablesState] = it.value.jvmOnlyFields
                iterateThroughContainerMembers(
                    cellId,
                    neededCallInstance,
                    serializedVariablesState.fieldDescriptor,
                    it.value.propertiesData,
                    currentDepth + 1
                )
            }
        }
    }

    private fun getSimpleTypeNameFrom(property: Field?, value: Any?): String? {
        return if (property != null) {
            val returnType = property.type
            returnType.simpleName
        } else {
            value?.toString()
        }
    }

    private fun getSimpleTypeNameFrom(property: KProperty<*>?, value: Any?): String? {
        return if (property != null) {
            val returnType = property.returnType
            val classifier = returnType.classifier
            if (classifier is KTypeParameter) {
                classifier.name
            } else {
                (classifier as KClass<*>).simpleName
            }
        } else {
            value?.toString()
        }
    }

    private fun createSerializeVariableState(name: String, simpleTypeName: String?, value: Any?): ProcessedSerializedVarsState {
        // make it exception-safe
        val membersProperties = try {
            if (value != null) value::class.declaredMemberProperties else null
        } catch (e: Throwable) {
            null
        }
        val javaClass = value?.javaClass
        val jvmFields = if (javaClass != null && javaClass.isMemberClass) {
            javaClass.declaredFields
        } else { null }

        val isContainer = if (membersProperties != null) (
            membersProperties.isNotEmpty() || value!!::class.java.isArray || (javaClass != null && javaClass.isMemberClass)
            ) else false
        val type = if (value != null && value::class.java.isArray) {
            "Array"
        } else if (isContainer && value is List<*>) {
            "SingletonList"
        } else {
            simpleTypeName.toString()
        }

        val serializedVariablesState = SerializedVariablesState(type, getProperString(value), isContainer)

        return ProcessedSerializedVarsState(serializedVariablesState, membersProperties, jvmFields)
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

    companion object {
        const val serializationEnvProperty = "jupyter.serialization.enabled"
    }
}

fun getProperString(value: Any?): String {
    fun print(builder: StringBuilder, containerSize: Int, index: Int, value: Any?) {
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
