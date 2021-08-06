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
    val descriptorsState: Map<String, SerializedVariablesState>
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
    val objectInstance: Any?
) {
    override fun equals(other: Any?): Boolean {
        if (other == null) return objectInstance == null
        if (objectInstance == null) return false
        if (other is RuntimeObjectWrapper) return objectInstance === other.objectInstance
        return objectInstance === other
    }

    override fun hashCode(): Int {
        return objectInstance?.hashCode() ?: 0
    }
}

fun Any?.toObjectWrapper(): RuntimeObjectWrapper = RuntimeObjectWrapper(this)

class VariablesSerializer(private val serializationDepth: Int = 2, private val serializationLimit: Int = 10000) {

    fun MutableMap<String, SerializedVariablesState?>.addDescriptor(value: Any?, name: String = value.toString()) {
        this[name] = createSerializeVariableState(
            name,
            if (value != null) value::class.simpleName else "null",
            value
        ).serializedVariablesState
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
            "Collection"
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

            val kProperties = try {
                if (value != null) value::class.declaredMemberProperties else {
                    null
                }
            } catch (ex: Exception) {null}
            val serializedVersion = SerializedVariablesState(simpleTypeName, getProperString(value), true)
            val descriptors = serializedVersion.fieldDescriptor

            // only for set case
            if (simpleTypeName == "Set" && kProperties == null) {
                value as Set<*>
                val size = value.size
                descriptors["size"] = createSerializeVariableState(
                    "size", "Int", size
                ).serializedVariablesState
                descriptors.addDescriptor(value, "data")
            }

            if (isDescriptorsNeeded) {
                kProperties?.forEach { prop ->
                    val name = prop.name
                    val propValue = value?.let {
                        try {
                            prop as KProperty1<Any, *>
                            val ans = if (prop.visibility == KVisibility.PUBLIC) {
                                // https://youtrack.jetbrains.com/issue/KT-44418
                                if (prop.name == "size") {
                                    if (isArray(value)) {
                                        value as Array<*>
                                        value.size
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
                    /*
                    if (value is Collection<*>) {
                        value.forEach {
                            iterateThrough(descriptors, it)
                        }
                    } else if (value is Array<*>) {
                        value.forEach {
                            iterateThrough(descriptors, it)
                        }
                    }*/
                }
            }

            return ProcessedSerializedVarsState(serializedVersion, kPropertiesData = kProperties)
        }
    }

    /**
     * Map of Map of seen objects.
     * First Key: cellId
     * Second Key: actual value
     * Value: serialized VariableState
     */
    private val seenObjectsPerCell: MutableMap<Int, MutableMap<RuntimeObjectWrapper, SerializedVariablesState>> = mutableMapOf()

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
     * Stores info computed descriptors in a cell
     */
    private val computedDescriptorsPerCell: MutableMap<Int, ProcessedDescriptorsState> = mutableMapOf()

    private val isSerializationActive: Boolean = System.getProperty(serializationSystemProperty)?.toBooleanStrictOrNull() ?: true

    /**
     * Cache for not recomputing unchanged variables
     */
    private val serializedVariablesCache: MutableMap<String, SerializedVariablesState> = mutableMapOf()

    fun serializeVariables(cellId: Int, variablesState: Map<String, VariableState>, unchangedVariables: Set<String>): Map<String, SerializedVariablesState> {
        if (!isSerializationActive) return emptyMap()

        if (seenObjectsPerCell.containsKey(cellId)) {
            seenObjectsPerCell[cellId]!!.clear()
        }
        if (variablesState.isEmpty()) {
            return emptyMap()
        }
        currentSerializeCount = 0

        val neededEntries = variablesState.filterKeys { unchangedVariables.contains(it) }

        val serializedData = neededEntries.mapValues { serializeVariableState(cellId, it.key, it.value) }
        serializedVariablesCache.putAll(serializedData)
        return serializedVariablesCache
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
        val propertiesData = evaluatedDescriptorsState.processedSerializedVarsToJavaProperties[serializedVariablesState]
        if (propertiesData == null && value != null && (value::class.java.isArray || value::class.java.isMemberClass)) {
            return serializeVariableState(cellId, propertyName, propertiesData, value, false)
        }
        val property = propertiesData?.firstOrNull {
            it.name == propertyName
        } ?: return serializedVariablesState

        return serializeVariableState(cellId, propertyName, property, value, false)
    }

    private fun serializeVariableState(cellId: Int, name: String?, variableState: VariableState?, isOverride: Boolean = true): SerializedVariablesState {
        if (!isSerializationActive || variableState == null || name == null) return SerializedVariablesState()
        return serializeVariableState(cellId, name, variableState.property, variableState.value, isOverride)
    }

    private fun serializeVariableState(cellId: Int, name: String, property: Field?, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, getSimpleTypeNameFrom(property, value), value)
        return doActualSerialization(cellId, processedData, value.toObjectWrapper(), isOverride)
    }

    private fun serializeVariableState(cellId: Int, name: String, property: KProperty<*>, value: Any?, isOverride: Boolean = true): SerializedVariablesState {
        val processedData = createSerializeVariableState(name, getSimpleTypeNameFrom(property, value), value)
        return doActualSerialization(cellId, processedData, value.toObjectWrapper(), isOverride)
    }

    private fun doActualSerialization(cellId: Int, processedData: ProcessedSerializedVarsState, value: RuntimeObjectWrapper, isOverride: Boolean = true): SerializedVariablesState {
        val serializedVersion = processedData.serializedVariablesState

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())

        if (isOverride) {
            computedDescriptorsPerCell[cellId] = ProcessedDescriptorsState()
        }
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]
        currentCellDescriptors!!.processedSerializedVarsToJavaProperties[serializedVersion] = processedData.propertiesData
        currentCellDescriptors.processedSerializedVarsToKTProperties[serializedVersion] = processedData.kPropertiesData

        if (value.objectInstance != null) {
            seenObjectsPerCell[cellId]!!.putIfAbsent(value, serializedVersion)
        }
        if (serializedVersion.isContainer) {
            // check for seen
            if (seenObjectsPerCell[cellId]!!.containsKey(value)) {
                val previouslySerializedState = seenObjectsPerCell[cellId]!![value] ?: return processedData.serializedVariablesState
                serializedVersion.fieldDescriptor += previouslySerializedState.fieldDescriptor
            }
            val type = processedData.propertiesType
            if (type == PropertiesType.KOTLIN) {
                iterateThroughContainerMembers(cellId, value.objectInstance, serializedVersion.fieldDescriptor, kProperties = currentCellDescriptors.processedSerializedVarsToKTProperties[serializedVersion])
            } else {
                iterateThroughContainerMembers(cellId, value.objectInstance, serializedVersion.fieldDescriptor, currentCellDescriptors.processedSerializedVarsToJavaProperties[serializedVersion])
            }
            iterateThroughContainerMembers(cellId, value.objectInstance, serializedVersion.fieldDescriptor, currentCellDescriptors.processedSerializedVarsToJavaProperties[serializedVersion])
        }

        return processedData.serializedVariablesState
    }

    private fun iterateThroughContainerMembers(
        cellId: Int,
        callInstance: Any?,
        descriptor: MutableFieldDescriptor,
        properties: PropertiesData? = null,
        kProperties: KPropertiesData? = null,
        currentDepth: Int = 0
    ) {
        fun iterateAndStoreValues(callInstance: Any, descriptorsState: MutableMap<String, SerializedVariablesState?>) {
            if (callInstance is Collection<*>) {
                callInstance.forEach {
                    descriptorsState.addDescriptor(it)
                }
            } else if (callInstance is Array<*>) {
                callInstance.forEach {
                    descriptorsState.addDescriptor(it)
                }
            }
        }

        if ((properties == null && kProperties == null && callInstance !is Set<*>) || callInstance == null || currentDepth >= serializationDepth) return

        val serializedIteration = mutableMapOf<String, ProcessedSerializedVarsState>()

        seenObjectsPerCell.putIfAbsent(cellId, mutableMapOf())
        val seenObjectsPerCell = seenObjectsPerCell[cellId]
        val currentCellDescriptors = computedDescriptorsPerCell[cellId]!!
        // ok, it's a copy on the left for some reason
        val instancesPerState = currentCellDescriptors.instancesPerState

        if (properties != null) {
            for (it in properties) {
                if (currentSerializeCount > serializationLimit) {
                    break
                }
                iterateThrough(it, seenObjectsPerCell, serializedIteration, descriptor, instancesPerState, callInstance)
                currentSerializeCount++
            }
        } else if (kProperties != null) {
            for (it in kProperties) {
                if (currentSerializeCount > serializationLimit) {
                    break
                }
                iterateThrough(it, seenObjectsPerCell, serializedIteration, descriptor, instancesPerState, callInstance)
                currentSerializeCount++
            }
        }

        if (currentSerializeCount > serializationLimit) {
            return
        }

        val isArrayType = checkForPossibleArray(callInstance)
        computedDescriptorsPerCell[cellId]!!.instancesPerState += instancesPerState

        if (descriptor.size == 2 && descriptor.containsKey("data")) {
            val listData = descriptor["data"]?.fieldDescriptor ?: return
            if (descriptor.containsKey("size") && descriptor["size"]?.value == "null") {
                descriptor.remove("size")
                descriptor.remove("data")
                iterateAndStoreValues(callInstance, descriptor)
            } else {
                iterateAndStoreValues(callInstance, listData)
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
                }.toObjectWrapper()

                computedDescriptorsPerCell[cellId]!!.instancesPerState += instancesPerState
                iterateThroughContainerMembers(
                    cellId,
                    neededCallInstance.objectInstance,
                    serializedVariablesState.fieldDescriptor,
                    it.value.propertiesData,
                    currentDepth = currentDepth + 1
                )
            }
        }
        /*
        if (descriptor.size == 2 && descriptor.containsKey("data")) {
            val listData = descriptor["data"]?.fieldDescriptor ?: return
            if (callInstance is Collection<*>) {
                callInstance.forEach {
                    listData.addDescriptor(it)
                }
            } else if (callInstance is Array<*>) {
                callInstance.forEach {
                    listData.addDescriptor(it)
                }
            }
        }*/
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
        callInstance: Any
    ) {
        contract {
            returns() implies (elem is Field || elem is KProperty1<*, *>)
        }

        val name = if (elem is Field) elem.name else (elem as KProperty1<Any, *>).name
        val value = if (elem is Field) tryGetValueFromProperty(elem, callInstance).toObjectWrapper()
        else {
            elem as KProperty1<Any, *>
            tryGetValueFromProperty(elem, callInstance).toObjectWrapper()
        }

        if (!seenObjectsPerCell!!.containsKey(value)) {
            val simpleType = if (elem is Field) getSimpleTypeNameFrom(elem, value.objectInstance) ?: "null"
            else {
                elem as KProperty1<Any, *>
                getSimpleTypeNameFrom(elem, value.objectInstance) ?: "null"
            }
            serializedIteration[name] = if (standardContainersUtilizer.isStandardType(simpleType)) {
                standardContainersUtilizer.serializeContainer(simpleType, value.objectInstance, true)
            } else {
                createSerializeVariableState(name, simpleType, value)
            }
            descriptor[name] = serializedIteration[name]!!.serializedVariablesState
        }
        if (descriptor[name] != null) {
            instancesPerState[descriptor[name]!!] = value.objectInstance
        }

        if (!seenObjectsPerCell.containsKey(value)) {
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
        return doCreateSerializedVarsState(simpleTypeName, value)
    }

    private fun createSerializeVariableState(name: String, simpleTypeName: String?, value: RuntimeObjectWrapper): ProcessedSerializedVarsState {
        return doCreateSerializedVarsState(simpleTypeName, value.objectInstance)
    }

    private fun doCreateSerializedVarsState(simpleTypeName: String?, value: Any?): ProcessedSerializedVarsState {
        val javaClass = value?.javaClass
        val membersProperties = javaClass?.declaredFields?.filter {
            !(it.name.startsWith("script$") || it.name.startsWith("serialVersionUID"))
        }

        val isContainer = if (membersProperties != null) (
            !primitiveWrappersSet.contains(javaClass) && membersProperties.isNotEmpty() || value is Set<*> || value::class.java.isArray || javaClass.isMemberClass
            ) else false
        val type = if (value != null && value::class.java.isArray) {
            "Array"
        } else {
            simpleTypeName.toString()
        }

        if (value != null && standardContainersUtilizer.isStandardType(type)) {
            return standardContainersUtilizer.serializeContainer(type, value)
        }

        val serializedVariablesState = SerializedVariablesState(type, getProperString(value), isContainer)

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

fun KClass<*>.isArray(): Boolean = this.isSubclassOf(Array::class)
fun KClass<*>.isMap(): Boolean = this.isSubclassOf(Map::class)
fun KClass<*>.isCollection(): Boolean = this.isSubclassOf(Collection::class)
fun KClass<*>.isNumber(): Boolean = this.isSubclassOf(Number::class)
