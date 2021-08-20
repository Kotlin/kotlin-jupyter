package org.jetbrains.kotlinx.jupyter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.compiler.util.SerializedVariablesState
import java.lang.reflect.Field
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

typealias FieldDescriptor = Map<String, SerializedVariablesState?>
typealias MutableFieldDescriptor = MutableMap<String, SerializedVariablesState?>
typealias KPropertiesData = Collection<KProperty1<out Any, *>>
typealias PropertiesData = Array<Field>

enum class PropertiesType {
    KOTLIN,
    JAVA,
    MIXED
}

@Serializable
data class SerializedCommMessageContent(
    val topLevelDescriptorName: String,
    val descriptorsState: Map<String, SerializedVariablesState>,
    val pathToDescriptor: List<String> = emptyList()
)

fun getVariablesDescriptorsFromJson(json: JsonObject): SerializedCommMessageContent {
    return Json.decodeFromJsonElement<SerializedCommMessageContent>(json)
}

class ProcessedSerializedVarsState(
    val serializedVariablesState: SerializedVariablesState,
    val propertiesData: PropertiesData? = null,
    val kPropertiesData: Collection<KProperty1<out Any, *>>? = null
) {
    val propertiesType: PropertiesType = if (propertiesData == null && kPropertiesData != null) PropertiesType.KOTLIN
    else if (propertiesData != null && kPropertiesData == null) PropertiesType.JAVA
    else if (propertiesData != null && kPropertiesData != null) PropertiesType.MIXED
    else PropertiesType.JAVA
}

data class ProcessedDescriptorsState(
    val processedSerializedVarsToJavaProperties: MutableMap<SerializedVariablesState, PropertiesData?> = mutableMapOf(),
    val processedSerializedVarsToKTProperties: MutableMap<SerializedVariablesState, KPropertiesData?> = mutableMapOf(),
    val instancesPerState: MutableMap<SerializedVariablesState, Any?> = mutableMapOf()
)

data class RuntimeObjectWrapper(
    val objectInstance: Any?,
    val isRecursive: Boolean = false
) {
    val computerID: String = Integer.toHexString(hashCode())

    override fun equals(other: Any?): Boolean {
        if (other == null) return objectInstance == null
        if (objectInstance == null) return false
        if (other is RuntimeObjectWrapper) return objectInstance === other.objectInstance
        return objectInstance === other
    }

    // TODO: it's not changing after recreation
    override fun hashCode(): Int {
        return if (isRecursive) Random.nextInt() else objectInstance?.hashCode() ?: 0
    }
}

fun Any?.toObjectWrapper(isRecursive: Boolean = false): RuntimeObjectWrapper = RuntimeObjectWrapper(this, isRecursive)

fun Any?.getToStringValue(isRecursive: Boolean = false): String {
    return if (isRecursive) {
        "${this!!::class.simpleName}: recursive structure"
    } else {
        try {
            this?.toString() ?: "null"
        } catch (e: StackOverflowError) {
            "${this!!::class.simpleName}: recursive structure"
        }
    }
}

fun Any?.getUniqueID(isRecursive: Boolean = false): String {
    return if (this != null && this !is Map.Entry<*, *>) {
        val hashCode = if (isRecursive) {
            Random.nextLong()
        } else {
            // ignore standard numerics
            if (this !is Number && this::class.simpleName != "int") {
                this.hashCode()
            } else {
                Random.nextLong()
            }
        }
        Integer.toHexString(hashCode.toInt())
    } else {
        ""
    }
}

/**
 * Provides contract for using threshold-based removal heuristic.
 * Every serialization-related info in [T] would be removed once [isShouldRemove] == true.
 * Default: T = Int, cellID
 */
interface ClearableSerializer<T> {
    fun isShouldRemove(currentState: T): Boolean

    suspend fun clearStateInfo(currentState: T)
}

class VariablesSerializer(
    private val serializationDepth: Int = 2,
    private val serializationLimit: Int = 10000,
    private val cellCountRemovalThreshold: Int = 5,
    // let's make this flag customizable from Jupyter config menu
    val shouldRemoveOldVariablesFromCache: Boolean = true
) : ClearableSerializer<Int> {

    fun MutableMap<String, SerializedVariablesState?>.addDescriptor(value: Any?, name: String = value.toString()) {
        val typeName = if (value != null) value::class.simpleName else "null"
        this[name] = createSerializeVariableState(
            name,
            typeName,
            value
        ).serializedVariablesState
        if (typeName != null && typeName == "Entry") {
            val descriptor = this[name]
            value as Map.Entry<*, *>
            val valueType = if (value.value != null) value.value!!::class.simpleName else "null"
            descriptor!!.fieldDescriptor[value.key.toString()] = createSerializeVariableState(
                value.key.toString(),
                valueType,
                value.value
            ).serializedVariablesState
        }
    }

    /**
     * Its' aim to serialize everything in Kotlin reflection since it much more straightforward
     */
    inner class StandardContainersUtilizer {
        private val containersTypes: Set<String> = setOf(
            "List",
            "SingletonList",
            "LinkedList",
            "Array",
            "Map",
            "Set",
            "Collection",
            "LinkedValues",
            "LinkedEntrySet"
        )

        fun isStandardType(type: String): Boolean = containersTypes.contains(type)

        fun serializeContainer(simpleTypeName: String, value: Any?, isDescriptorsNeeded: Boolean = false): ProcessedSerializedVarsState {
            return doSerialize(simpleTypeName, value, isDescriptorsNeeded)
        }

        private fun doSerialize(simpleTypeName: String, value: Any?, isDescriptorsNeeded: Boolean = false): ProcessedSerializedVarsState {
            fun isArray(value: Any?): Boolean {
                return value?.let {
                    value::class.java.isArray
                } == true
            }
            fun getProperEntrySetRepresentation(value: Any?): String {
                value as Set<*>
                val size = value.size
                if (size == 0) return ""
                val firstProper = value.firstOrNull {
                    it as Map.Entry<*, *>
                    it.key != null && it.value != null
                } as Map.Entry<*, *> ?: return ""
                return "<${firstProper.key!!::class.simpleName}, ${firstProper.value!!::class.simpleName}>"
            }

            val kProperties = try {
                if (value != null) value::class.declaredMemberProperties else {
                    null
                }
            } catch (ex: Exception) { null }
            val stringedValue = getProperString(value)
            val varID = if (value !is String) {
                val isRecursive = stringedValue.contains(": recursive structure")
                if (!isRecursive && simpleTypeName == "LinkedEntrySet") {
                    getProperEntrySetRepresentation(value)
                } else {
                    value.getUniqueID(isRecursive)
                }
            } else {
                ""
            }
            val serializedVersion = SerializedVariablesState(simpleTypeName, stringedValue, true, varID)
            val descriptors = serializedVersion.fieldDescriptor

            // only for set case
            if (simpleTypeName == "Set" && kProperties == null && value != null) {
                value as Set<*>
                val size = value.size
                descriptors["size"] = createSerializeVariableState(
                    "size",
                    "Int",
                    size
                ).serializedVariablesState
                descriptors.addDescriptor(value, "data")
            }

            if (isDescriptorsNeeded) {
                kProperties?.forEach { prop ->
                    val name = prop.name
                    if (name == "null") {
                        return@forEach
                    }
                    val propValue = value?.let {
                        try {
                            prop as KProperty1<Any, *>
                            val ans = if (prop.visibility == KVisibility.PUBLIC) {
                                // https://youtrack.jetbrains.com/issue/KT-44418
                                if (prop.name == "size") {
                                    if (isArray(value)) {
                                        value as Array<*>
                                        // there might be size 10, but only one actual recursive value
                                        val runTimeSize = value.size
                                        if (runTimeSize > 5 && value[0] is List<*> && value[1] == null && value [2] == null) {
                                            1
                                        } else {
                                            runTimeSize
                                        }
                                    } else {
                                        value as Collection<*>
                                        value.size
                                    }
                                } else {
                                    prop.get(value)
                                }
                            } else {
                                val wasAccessible = prop.isAccessible
                                prop.isAccessible = true
                                val res = prop.get(value)
                                prop.isAccessible = wasAccessible
                                res
                            }
                            ans
                        } catch (ex: Exception) {
                            null
                        }
                    }

                    // might skip here redundant size always nullable
                    /*
                    if (propValue == null && name == "size" && isArray(value)) {
                        return@forEach
                    }
                    */
                    descriptors[name] = createSerializeVariableState(
                        name,
                        getSimpleTypeNameFrom(prop, propValue),
                        propValue
                    ).serializedVariablesState
                }

                /**
                 * Note: standard arrays are used as storage in many classes with only one field - size.
                 * Hence, create a custom descriptor data where would be actual values.
                 */
                if (descriptors.size == 1 && descriptors.entries.first().key == "size") {
                    descriptors.addDescriptor(value, "data")
                }
            }

            return ProcessedSerializedVarsState(serializedVersion, kPropertiesData = kProperties)
        }
    }

    /**
     * Map of Map of seen objects related to a particular variable serialization
     * First Key: topLevel variable Name
     * Second Key: actual value
     * Value: serialized VariableState
     */
    private val seenObjectsPerVariable: MutableMap<String, MutableMap<RuntimeObjectWrapper, SerializedVariablesState>> = mutableMapOf()

    private var currentSerializeCount: Int = 0

    private val standardContainersUtilizer = StandardContainersUtilizer()

    private val primitiveWrappersSet: Set<Class<*>> = setOf(
        Byte::class.java,
        Short::class.java,
        Int::class.java,
        Integer::class.java,
        Long::class.java,
        Float::class.java,
        Double::class.java,
        Char::class.java,
        Boolean::class.java,
        String::class.java
    )

    /**
     * Stores info computed descriptors in a cell starting from the very variable as a root
     */
    private val computedDescriptorsPerCell: MutableMap<Int, MutableMap<String, ProcessedDescriptorsState>> = mutableMapOf()

    private val isSerializationActive: Boolean = System.getProperty(serializationSystemProperty)?.toBooleanStrictOrNull() ?: true

    /**
     * Cache for not recomputing unchanged variables
     */
    private val serializedVariablesCache: MutableMap<String, SerializedVariablesState> = mutableMapOf()

    private val removedFromSightVariables: MutableSet<String> = mutableSetOf()

    private suspend fun clearOldData(currentCellId: Int, cellVariables: Map<Int, Set<String>>) {
        fun removeFromCache(cellId: Int) {
            val oldDeclarations = cellVariables[cellId]
            oldDeclarations?.let { oldSet ->
                oldSet.forEach { varName ->
                    serializedVariablesCache.remove(varName)
                    removedFromSightVariables.add(varName)
                }
            }
        }

        val setToRemove = mutableSetOf<Int>()
        computedDescriptorsPerCell.forEach { (cellNumber, _) ->
            if (abs(currentCellId - cellNumber) >= cellCountRemovalThreshold) {
                setToRemove.add(cellNumber)
            }
        }
        log.debug("Removing old info about cells: $setToRemove")
        setToRemove.forEach {
            clearStateInfo(it)
            if (shouldRemoveOldVariablesFromCache) {
                removeFromCache(it)
            }
        }
    }

    override fun isShouldRemove(currentState: Int): Boolean {
        return computedDescriptorsPerCell.size >= cellCountRemovalThreshold
    }

    override suspend fun clearStateInfo(currentState: Int) {
        computedDescriptorsPerCell.remove(currentState)
        // seenObjectsPerVariable.remove(currentState)
    }

    suspend fun tryValidateCache(currentCellId: Int, cellVariables: Map<Int, Set<String>>) {
        if (!isShouldRemove(currentCellId)) return
        clearOldData(currentCellId, cellVariables)
    }

    fun serializeVariables(cellId: Int, variablesState: Map<String, VariableState>, oldDeclarations: Map<String, Int>, variablesCells: Map<String, Int>, unchangedVariables: Set<String>): Map<String, SerializedVariablesState> {
        fun removeNonExistingEntries() {
            val toRemoveSet = mutableSetOf<String>()
            serializedVariablesCache.forEach { (name, _) ->
                // seems like this never gonna happen
                if (!variablesState.containsKey(name)) {
                    toRemoveSet.add(name)
                }
            }
            toRemoveSet.forEach { serializedVariablesCache.remove(it) }
        }

        if (!isSerializationActive) return emptyMap()

        if (variablesState.isEmpty()) {
            return emptyMap()
        }
        currentSerializeCount = 0
        val neededEntries = variablesState.filterKeys {
            val wasRedeclared = !unchangedVariables.contains(it)
            if (wasRedeclared) {
                removedFromSightVariables.remove(it)
            }
            // TODO: might consider self-recursive elements always to recompute since it's non comparable via strings
            if (serializedVariablesCache.isEmpty()) {
                true
            } else {
                (!unchangedVariables.contains(it) || serializedVariablesCache[it]?.value != variablesState[it]?.stringValue) &&
                    !removedFromSightVariables.contains(it)
            }
        }
        log.debug("Variables state as is: $variablesState")
        log.debug("Serializing variables after filter: $neededEntries")
        log.debug("Unchanged variables: ${unchangedVariables - neededEntries.keys}")

        // remove previous data
        val serializedData = neededEntries.mapValues {
            val actualCell = variablesCells[it.key] ?: cellId
            if (oldDeclarations.containsKey(it.key)) {
                val oldCell = oldDeclarations[it.key]!!
                computedDescriptorsPerCell[oldCell]?.remove(it.key)
                seenObjectsPerVariable.remove(it.key)
            }
            serializeVariableState(actualCell, it.key, it.value)
        }

        serializedVariablesCache.putAll(serializedData)
        removeNonExistingEntries()
        log.debug(serializedVariablesCache.entries.toString())

        return serializedVariablesCache
    }

    fun doIncrementalSerialization(
        cellId: Int,
        topLevelName: String,
        propertyName: String,
        serializedVariablesState: SerializedVariablesState,
        pathToDescriptor: List<String> = emptyList()
    ): SerializedVariablesState {
        if (!isSerializationActive) return serializedVariablesState

        val cellDescriptors = computedDescriptorsPerCell[cellId] ?: return serializedVariablesState
        return updateVariableState(cellId, propertyName, cellDescriptors[topLevelName]!!, serializedVariablesState)
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
        val propertiesData = evaluatedDescriptorsState.processedSerializedVarsToJavaProperties[serializedVariablesState]
        if (value != null && (value::class.java.isArray || value::class.java.isMemberClass)) {
            return serializeVariableState(cellId, propertyName, propertiesData?.firstOrNull(), value, false)
        }
        val property = propertiesData?.firstOrNull {
            it.name == propertyName
        } ?: return serializedVariablesState

        return serializeVariableState(cellId, propertyName, property, value, isRecursive = false, false)
    }

    private fun serializeVariableState(cellId: Int, topLevelName: String?, variableState: VariableState?, isOverride: Boolean = true): SerializedVariablesState {
        if (!isSerializationActive || variableState == null || topLevelName == null) return SerializedVariablesState()
        // force recursive check
        variableState.stringValue
        return serializeVariableState(cellId, topLevelName, variableState.property, variableState.value.getOrNull(), variableState.isRecursive, isOverride)
    }

    private fun serializeVariableState(cellId: Int, topLevelName: String, property: Field?, value: Any?, isRecursive: Boolean, isOverride: Boolean = true): SerializedVariablesState {
        val wrapper = value.toObjectWrapper(isRecursive)
        val processedData = createSerializeVariableState(topLevelName, getSimpleTypeNameFrom(property, value), wrapper)
        return doActualSerialization(cellId, topLevelName, processedData, wrapper, isRecursive, isOverride)
    }

    private fun serializeVariableState(cellId: Int, topLevelName: String, property: KProperty<*>, value: Any?, isRecursive: Boolean, isOverride: Boolean = true): SerializedVariablesState {
        val wrapper = value.toObjectWrapper(isRecursive)
        val processedData = createSerializeVariableState(topLevelName, getSimpleTypeNameFrom(property, value), wrapper)
        return doActualSerialization(cellId, topLevelName, processedData, wrapper, isRecursive, isOverride)
    }

    private fun doActualSerialization(cellId: Int, topLevelName: String, processedData: ProcessedSerializedVarsState, value: RuntimeObjectWrapper, isRecursive: Boolean, isOverride: Boolean = true): SerializedVariablesState {
        fun checkIsNotStandardDescriptor(descriptor: MutableMap<String, SerializedVariablesState?>): Boolean {
            return descriptor.isNotEmpty() && !descriptor.containsKey("size") && !descriptor.containsKey("data")
        }
        val serializedVersion = processedData.serializedVariablesState

        seenObjectsPerVariable.putIfAbsent(topLevelName, mutableMapOf())
        computedDescriptorsPerCell.putIfAbsent(cellId, mutableMapOf())

        if (isOverride) {
            val instances = computedDescriptorsPerCell[cellId]?.get(topLevelName)?.instancesPerState
            computedDescriptorsPerCell[cellId]!![topLevelName] = ProcessedDescriptorsState()
            if (instances != null) {
                computedDescriptorsPerCell[cellId]!![topLevelName]!!.instancesPerState += instances
            }
        }
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]?.get(topLevelName)
        // TODO should we stack?
        // i guess, not
        currentCellDescriptors!!.processedSerializedVarsToJavaProperties[serializedVersion] = processedData.propertiesData
        currentCellDescriptors.processedSerializedVarsToKTProperties[serializedVersion] = processedData.kPropertiesData

        if (value.objectInstance != null) {
            seenObjectsPerVariable[topLevelName]!!.putIfAbsent(value, serializedVersion)
        }
        if (serializedVersion.isContainer) {
            // check for seen
            if (seenObjectsPerVariable[topLevelName]!!.containsKey(value)) {
                val previouslySerializedState = seenObjectsPerVariable[topLevelName]!![value] ?: return processedData.serializedVariablesState
                serializedVersion.fieldDescriptor += previouslySerializedState.fieldDescriptor
                if (checkIsNotStandardDescriptor(serializedVersion.fieldDescriptor)) {
                    return serializedVersion
                }
            }
            val type = processedData.propertiesType
            if (type == PropertiesType.KOTLIN) {
                val kProperties = currentCellDescriptors.processedSerializedVarsToKTProperties[serializedVersion]
                if (kProperties?.size == 1 && kProperties.first().name == "size") {
                    serializedVersion.fieldDescriptor.addDescriptor(value.objectInstance, "data")
                }
                iterateThroughContainerMembers(cellId, topLevelName, value.objectInstance, serializedVersion.fieldDescriptor, isRecursive = isRecursive, kProperties = currentCellDescriptors.processedSerializedVarsToKTProperties[serializedVersion])
            } else {
                iterateThroughContainerMembers(cellId, topLevelName, value.objectInstance, serializedVersion.fieldDescriptor, isRecursive = isRecursive, currentCellDescriptors.processedSerializedVarsToJavaProperties[serializedVersion])
            }
        }

        return processedData.serializedVariablesState
    }

    private fun iterateThroughContainerMembers(
        cellId: Int,
        topLevelName: String,
        callInstance: Any?,
        descriptor: MutableFieldDescriptor,
        isRecursive: Boolean = false,
        properties: PropertiesData? = null,
        kProperties: KPropertiesData? = null,
        currentDepth: Int = 0
    ) {
        fun iterateAndStoreValues(callInstance: Any, descriptorsState: MutableMap<String, SerializedVariablesState?>) {
            if (callInstance is Collection<*>) {
                callInstance.forEach {
                    descriptorsState.addDescriptor(it, name = it.getToStringValue())
                }
            } else if (callInstance is Array<*>) {
                callInstance.forEach {
                    descriptorsState.addDescriptor(it, name = it.getToStringValue())
                }
            }
        }

        if ((properties == null && kProperties == null && callInstance !is Set<*>) || callInstance == null || currentDepth >= serializationDepth) return

        val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()

        seenObjectsPerVariable.putIfAbsent(topLevelName, mutableMapOf())
        val seenObjectsPerCell = seenObjectsPerVariable[topLevelName]
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]!![topLevelName]!!
        // ok, it's a copy on the left for some reason
        val instancesPerState = currentCellDescriptors.instancesPerState

        if (properties != null) {
            for (it in properties) {
                if (currentSerializeCount > serializationLimit) {
                    break
                }
                iterateThrough(it, seenObjectsPerCell, serializedIteration, descriptor, instancesPerState, callInstance, isRecursive)
                currentSerializeCount++
            }
        } else if (kProperties != null) {
            for (it in kProperties) {
                if (currentSerializeCount > serializationLimit) {
                    break
                }
                iterateThrough(it, seenObjectsPerCell, serializedIteration, descriptor, instancesPerState, callInstance, isRecursive)
                currentSerializeCount++
            }
        }

        if (currentSerializeCount > serializationLimit) {
            return
        }

        val isArrayType = checkForPossibleArray(callInstance)
        computedDescriptorsPerCell[cellId]!![topLevelName]!!.instancesPerState += instancesPerState

        if (descriptor.size == 2 && (descriptor.containsKey("data") || descriptor.containsKey("element"))) {
            val singleElemMode = descriptor.containsKey("element")
            val listData = if (!singleElemMode) descriptor["data"]?.fieldDescriptor else {
                descriptor["element"]?.fieldDescriptor
            } ?: return
            if (descriptor.containsKey("size") && descriptor["size"]?.value == "null") {
                descriptor.remove("size")
                descriptor.remove("data")
                iterateAndStoreValues(callInstance, descriptor)
            } else {
                iterateAndStoreValues(callInstance, listData)
            }
        }

//        if (isRecursive) {
//            return
//        }

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
                }.toObjectWrapper(isRecursive)

                computedDescriptorsPerCell[cellId]!![topLevelName]!!.instancesPerState += instancesPerState
                iterateThroughContainerMembers(
                    cellId,
                    topLevelName,
                    neededCallInstance.objectInstance,
                    serializedVariablesState.fieldDescriptor,
                    isRecursive = isRecursive,
                    properties = it.value.propertiesData,
                    currentDepth = currentDepth + 1
                )
            }
        }
    }

    /**
     * Really wanted to use contracts here, but all usages should be provided with this annotation and,
     * perhaps, it may be a big overhead
     */
    @OptIn(ExperimentalContracts::class)
    private fun iterateThrough(
        elem: Any,
        seenObjectsPerCell: MutableMap<RuntimeObjectWrapper, SerializedVariablesState>?,
        serializedIteration: MutableMap<String, ProcessedSerializedVarsState>,
        descriptor: MutableFieldDescriptor,
        instancesPerState: MutableMap<SerializedVariablesState, Any?>,
        callInstance: Any,
        isRecursive: Boolean = false
    ) {
        contract {
            returns() implies (elem is Field || elem is KProperty1<*, *>)
        }

        val name = if (elem is Field) elem.name else (elem as KProperty1<Any, *>).name
        val value = if (elem is Field) tryGetValueFromProperty(elem, callInstance).toObjectWrapper(isRecursive)
        else {
            elem as KProperty1<Any, *>
            tryGetValueFromProperty(elem, callInstance).toObjectWrapper(isRecursive)
        }

        val simpleType = if (elem is Field) getSimpleTypeNameFrom(elem, value.objectInstance) ?: "null"
        else {
            elem as KProperty1<Any, *>
            getSimpleTypeNameFrom(elem, value.objectInstance) ?: "null"
        }
        serializedIteration[name] = if (standardContainersUtilizer.isStandardType(simpleType)) {
            // TODO might add isRecursive
            standardContainersUtilizer.serializeContainer(simpleType, value.objectInstance, true)
        } else {
            createSerializeVariableState(name, simpleType, value)
        }
        descriptor[name] = serializedIteration[name]!!.serializedVariablesState

        if (descriptor[name] != null) {
            instancesPerState[descriptor[name]!!] = value.objectInstance
        }

        if (seenObjectsPerCell?.containsKey(value) == false) {
            if (descriptor[name] != null) {
                seenObjectsPerCell[value] = descriptor[name]!!
            }
        }
    }

    private fun getSimpleTypeNameFrom(property: Field?, value: Any?): String? {
        return if (property != null) {
            val returnType = property.type
            returnType.simpleName
        } else {
            if (value != null) {
                value::class.simpleName
            } else {
                value?.getToStringValue()
            }
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
            value?.getToStringValue()
        }
    }

    private fun createSerializeVariableState(name: String, simpleTypeName: String?, value: Any?): ProcessedSerializedVarsState {
        return doCreateSerializedVarsState(simpleTypeName, value)
    }

    private fun createSerializeVariableState(name: String, simpleTypeName: String?, value: RuntimeObjectWrapper): ProcessedSerializedVarsState {
        return doCreateSerializedVarsState(simpleTypeName, value.objectInstance, value.computerID)
    }

    private fun doCreateSerializedVarsState(simpleTypeName: String?, value: Any?, uniqueID: String? = null): ProcessedSerializedVarsState {
        val javaClass = value?.javaClass
        val membersProperties = javaClass?.declaredFields?.filter {
            !(it.name.startsWith("script$") || it.name.startsWith("serialVersionUID"))
        }

        val type = if (value != null && value::class.java.isArray) {
            "Array"
        } else {
            simpleTypeName.toString()
        }
        val isContainer = if (membersProperties != null) (
            !primitiveWrappersSet.contains(javaClass) && type != "Entry" && membersProperties.isNotEmpty() || value is Set<*> || value::class.java.isArray || (javaClass.isMemberClass && type != "Entry")
            ) else false

        if (value != null && standardContainersUtilizer.isStandardType(type)) {
            return standardContainersUtilizer.serializeContainer(type, value)
        }
        val stringedValue = getProperString(value)
        val finalID = uniqueID
            ?: if (value !is String) {
                value.getUniqueID(stringedValue.contains(": recursive structure"))
            } else {
                ""
            }

        val serializedVariablesState = SerializedVariablesState(type, getProperString(value), isContainer, finalID)

        return ProcessedSerializedVarsState(serializedVariablesState, membersProperties?.toTypedArray())
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

    // use of Java 9 required
    @SuppressWarnings("DEPRECATION")
    private fun tryGetValueFromProperty(property: Field, callInstance: Any): Any? {
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

    private fun checkForPossibleArray(callInstance: Any): Boolean {
        // consider arrays and singleton lists
        return callInstance::class.java.isArray || callInstance is List<*> || callInstance is Array<*>
    }

    companion object {
        const val serializationSystemProperty = "jupyter.serialization.enabled"
    }
}

fun getProperString(value: Any?): String {
    fun print(builder: StringBuilder, containerSize: Int, index: Int, value: Any?, mapMode: Boolean = false) {
        if (index != containerSize - 1) {
            if (mapMode) {
                value as Map.Entry<*, *>
                builder.append(value.key, '=', value.value, ", ")
            } else {
                builder.append(value, ", ")
            }
        } else {
            if (mapMode) {
                value as Map.Entry<*, *>
                builder.append(value.key, '=', value.value)
            } else {
                builder.append(value)
            }
        }
    }

    value ?: return "null"

    val kClass = value::class
    val isFromJavaArray = kClass.java.isArray

    return try {
        if (isFromJavaArray || kClass.isArray()) {
            value as Array<*>
            return buildString {
                val size = value.size
                value.forEachIndexed { index, it ->
                    print(this, size, index, it)
                }
            }
        }
        val isNumber = kClass.isNumber()
        if (isNumber) {
            value as Number
            return value.toString()
        }

        val isCollection = kClass.isCollection()

        if (isCollection) {
            value as Collection<*>
            return buildString {
                val size = value.size
                value.forEachIndexed { index, it ->
                    print(this, size, index, it)
                }
            }
        }
        val isMap = kClass.isMap()
        if (isMap) {
            value as Map<*, *>
            val size = value.size
            var ind = 0
            return buildString {
                value.forEach {
                    print(this, size, ind++, it, true)
                }
            }
        }
        value.toString()
    } catch (e: Throwable) {
        if (e is StackOverflowError) {
            "${value::class.simpleName}: recursive structure"
        } else {
            value.toString()
        }
    }
}

fun KClass<*>.isArray(): Boolean = this.isSubclassOf(Array::class)
fun KClass<*>.isMap(): Boolean = this.isSubclassOf(Map::class)
fun KClass<*>.isCollection(): Boolean = this.isSubclassOf(Collection::class)
fun KClass<*>.isNumber(): Boolean = this.isSubclassOf(Number::class)
