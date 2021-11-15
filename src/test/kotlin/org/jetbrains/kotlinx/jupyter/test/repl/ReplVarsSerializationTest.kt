package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ReplVarsSerializationTest : AbstractSingleReplTest() {
    override val repl = makeSimpleRepl()

    @Test
    fun simpleContainerSerialization() {
        val res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 2
        varsData shouldContainKey "x"
        varsData shouldContainKey "f"

        val listData = varsData["x"]!!
        listData.isContainer shouldBe true
        listData.fieldDescriptor.size shouldBe 2
        val listDescriptors = listData.fieldDescriptor

        listDescriptors["size"]!!.value shouldBe "4"
        listDescriptors["size"]!!.isContainer shouldBe false

        val actualContainer = listDescriptors.entries.first().value!!
        actualContainer.fieldDescriptor.size shouldBe 2
        actualContainer.isContainer shouldBe true
        actualContainer.value shouldBe listOf(1, 2, 3, 4).toString().substring(1, actualContainer.value!!.length + 1)

        val serializer = repl.variablesSerializer
        serializer.doIncrementalSerialization(0, "x", "data", actualContainer)
    }

    @Test
    fun testUnchangedVarsRedefinition() {
        val res = eval(
            """
            val x = listOf(1, 2, 3, 4)
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 2
        varsData.shouldContainKeys("x", "f")
        var unchangedVariables = repl.notebook.unchangedVariables
        unchangedVariables.isNotEmpty() shouldBe true

        eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 1
        )
        unchangedVariables = repl.notebook.unchangedVariables
        unchangedVariables.shouldContainAll("x", "f")
    }

    @Test
    fun moreThanDefaultDepthContainerSerialization() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1
        varsData.containsKey("x") shouldBe true

        val listData = varsData["x"]!!
        listData.isContainer shouldBe true
        listData.fieldDescriptor.size shouldBe 2
        val listDescriptors = listData.fieldDescriptor

        listDescriptors["size"]!!.value shouldBe "4"
        listDescriptors["size"]!!.isContainer shouldBe false

        val actualContainer = listDescriptors.entries.first().value!!
        actualContainer.fieldDescriptor.size shouldBe 2
        actualContainer.isContainer shouldBe true

        actualContainer.fieldDescriptor.forEach { (name, serializedState) ->
            if (name == "size") {
                serializedState!!.value shouldBe "4"
            } else {
                serializedState!!.fieldDescriptor.size shouldBe 0
                serializedState.isContainer shouldBe true
            }
        }
    }

    @Test
    fun cyclicReferenceTest() {
        val res = eval(
            """
            class C {
                inner class Inner;
                val i = Inner()
                val counter = 0
            }                
            val c = C()
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1
        varsData shouldContainKey "c"

        val serializedState = varsData["c"]!!
        serializedState.isContainer shouldBe true
        val descriptor = serializedState.fieldDescriptor
        descriptor.size shouldBe 2
        descriptor["counter"]!!.value shouldBe "0"

        val serializer = repl.variablesSerializer

        serializer.doIncrementalSerialization(0, "c", "i", descriptor["i"]!!)
    }

    @Test
    fun incrementalUpdateTest() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1

        val listData = varsData["x"]!!
        listData.isContainer shouldBe true
        listData.fieldDescriptor.size shouldBe 2
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val serializer = repl.variablesSerializer

        val newData = serializer.doIncrementalSerialization(0, "x", listData.fieldDescriptor.entries.first().key, actualContainer)
        val receivedDescriptor = newData.fieldDescriptor
        receivedDescriptor.size shouldBe 4

        var values = 1
        receivedDescriptor.forEach { (_, state) ->
            val fieldDescriptor = state!!.fieldDescriptor
            fieldDescriptor.size shouldBe 1
            state.isContainer shouldBe true
            state.value shouldBe "${values++}"
        }

        val depthMostNode = actualContainer.fieldDescriptor.entries.first { it.value!!.isContainer }
        val serializationAns = serializer.doIncrementalSerialization(0, "x", depthMostNode.key, depthMostNode.value!!)
    }

    @Test
    fun incrementalUpdateTestWithPath() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        val listData = varsData["x"]!!
        listData.fieldDescriptor.size shouldBe 2
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val serializer = repl.variablesSerializer
        val path = listOf("x", "a")

        val newData = serializer.doIncrementalSerialization(0, "x", listData.fieldDescriptor.entries.first().key, actualContainer, path)
        val receivedDescriptor = newData.fieldDescriptor
        receivedDescriptor.size shouldBe 4

        var values = 1
        receivedDescriptor.forEach { (_, state) ->
            val fieldDescriptor = state!!.fieldDescriptor
            fieldDescriptor.size shouldBe 1
            state.isContainer shouldBe true
            state.value shouldBe "${values++}"
        }
    }

    @Test
    fun testMapContainer() {
        val res = eval(
            """
            val x = mapOf(1 to "a", 2 to "b", 3 to "c", 4 to "c")
            val m = mapOf(1 to "a")
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 2
        varsData shouldContainKey "x"

        val mapData = varsData["x"]!!
        mapData.isContainer shouldBe true
        mapData.fieldDescriptor.size shouldBe 6
        val listDescriptors = mapData.fieldDescriptor

        listDescriptors.shouldContainKeys("values", "entries", "keys")

        val valuesDescriptor = listDescriptors["values"]!!
        valuesDescriptor.fieldDescriptor["size"]!!.value shouldBe "4"
        valuesDescriptor.fieldDescriptor["data"]!!.isContainer shouldBe true

        val serializer = repl.variablesSerializer

        var newData = serializer.doIncrementalSerialization(0, "x", "values", valuesDescriptor)
        var newDescriptor = newData.fieldDescriptor
        newDescriptor["size"]!!.value shouldBe "4"
        newDescriptor["data"]!!.fieldDescriptor.size shouldBe 3
        val ansSet = mutableSetOf("a", "b", "c")
        newDescriptor["data"]!!.fieldDescriptor.forEach { (_, state) ->
            state!!.isContainer shouldBe false
            ansSet.contains(state.value) shouldBe true
            ansSet.remove(state.value)
        }
        ansSet.isEmpty() shouldBe true

        val entriesDescriptor = listDescriptors["entries"]!!
        valuesDescriptor.fieldDescriptor["size"]!!.value shouldBe "4"
        valuesDescriptor.fieldDescriptor["data"]!!.isContainer shouldBe true
        newData = serializer.doIncrementalSerialization(0, "x", "entries", entriesDescriptor)
        newDescriptor = newData.fieldDescriptor
        newDescriptor["size"]!!.value shouldBe "4"
        newDescriptor["data"]!!.fieldDescriptor.size shouldBe 4
        ansSet.add("1=a")
        ansSet.add("2=b")
        ansSet.add("3=c")
        ansSet.add("4=c")

        newDescriptor["data"]!!.fieldDescriptor.forEach { (_, state) ->
            state!!.isContainer shouldBe false
            ansSet shouldContain state.value
            ansSet.remove(state.value)
        }
        ansSet.isEmpty() shouldBe true
    }

    @Test
    fun testSetContainer() {
        var res = eval(
            """
            val x = setOf("a", "b", "cc", "c")
            """.trimIndent(),
            jupyterId = 1
        )
        var varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1
        varsData shouldContainKey "x"

        var setData = varsData["x"]!!
        setData.isContainer shouldBe true
        setData.fieldDescriptor.size shouldBe 2
        var setDescriptors = setData.fieldDescriptor
        setDescriptors["size"]!!.value shouldBe "4"
        setDescriptors["data"]!!.isContainer shouldBe true
        setDescriptors["data"]!!.fieldDescriptor.size shouldBe 4
        setDescriptors["data"]!!.fieldDescriptor["a"]!!.value shouldBe "a"
        setDescriptors["data"]!!.fieldDescriptor.keys shouldContainAll setOf("b", "cc", "c")

        res = eval(
            """
            val c = mutableSetOf("a", "b", "cc", "c")
            """.trimIndent(),
            jupyterId = 2
        )
        varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 2
        varsData shouldContainKey "c"

        setData = varsData["c"]!!
        setData.isContainer shouldBe true
        setData.fieldDescriptor.size shouldBe 2
        setDescriptors = setData.fieldDescriptor
        setDescriptors["size"]!!.value shouldBe "4"
        setDescriptors["data"]!!.isContainer shouldBe true
        setDescriptors["data"]!!.fieldDescriptor.size shouldBe 4
        setDescriptors["data"]!!.fieldDescriptor["a"]!!.value shouldBe "a"
        setDescriptors["data"]!!.fieldDescriptor.keys shouldContainAll setOf("b", "cc", "c")
    }

    @Test
    fun testSerializationMessage() {
        val res = eval(
            """
            val x = listOf(listOf(1), listOf(2), listOf(3), listOf(4))
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1
        val listData = varsData["x"]!!
        listData.isContainer shouldBe true
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val propertyName = listData.fieldDescriptor.entries.first().key

        runBlocking {
            repl.serializeVariables(1, "x", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                data.isNotEmpty() shouldBe true

                val innerList = data.entries.last().value
                innerList.isContainer shouldBe true
                val receivedDescriptor = innerList.fieldDescriptor

                receivedDescriptor.size shouldBe 4
                var values = 1
                receivedDescriptor.forEach { (_, state) ->
                    val fieldDescriptor = state!!.fieldDescriptor
                    fieldDescriptor.size shouldBe 1
                    state.isContainer shouldBe true
                    state.value shouldBe "${values++}"
                }
            }
        }

        runBlocking {
            repl.serializeVariables("x", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                data.isNotEmpty() shouldBe true

                val innerList = data.entries.last().value
                innerList.isContainer shouldBe true
                val receivedDescriptor = innerList.fieldDescriptor

                receivedDescriptor.size shouldBe 4
                var values = 1
                receivedDescriptor.forEach { (_, state) ->
                    val fieldDescriptor = state!!.fieldDescriptor
                    fieldDescriptor.size shouldBe 1
                    state.isContainer shouldBe true
                    state.value shouldBe "${values++}"
                }
            }
        }
    }

    @Test
    fun testCyclicSerializationMessage() {
        val res = eval(
            """
            class C {
                inner class Inner;
                val i = Inner()
                val counter = 0
            }
            val c = C()
            """.trimIndent(),
            jupyterId = 1
        )
        val varsData = res.metadata.evaluatedVariablesState
        varsData.size shouldBe 1
        val listData = varsData["c"]!!
        listData.isContainer shouldBe true
        val actualContainer = listData.fieldDescriptor.entries.first().value!!
        val propertyName = listData.fieldDescriptor.entries.first().key

        runBlocking {
            repl.serializeVariables(1, "c", mapOf(propertyName to actualContainer)) { result ->
                val data = result.descriptorsState
                data.isNotEmpty() shouldBe true

                val innerList = data.entries.last().value
                innerList.isContainer shouldBe true
                val receivedDescriptor = innerList.fieldDescriptor
                receivedDescriptor.size shouldBe 1
                val originalClass = receivedDescriptor.entries.first().value!!
                originalClass.fieldDescriptor.size shouldBe 2
                originalClass.fieldDescriptor.keys shouldContainAll listOf("i", "counter")

                val anotherI = originalClass.fieldDescriptor["i"]!!
                runBlocking {
                    repl.serializeVariables(1, "c", mapOf(propertyName to anotherI)) { res ->
                        val data = res.descriptorsState
                        val innerList = data.entries.last().value
                        innerList.isContainer shouldBe true
                        val receivedDescriptor = innerList.fieldDescriptor
                        receivedDescriptor.size shouldBe 1
                        val originalClass = receivedDescriptor.entries.first().value!!
                        originalClass.fieldDescriptor.size shouldBe 2
                        originalClass.fieldDescriptor.keys shouldContainAll listOf("i", "counter")
                    }
                }
            }
        }
    }

    @Test
    fun testUnchangedVariablesSameCell() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        val state = repl.notebook.unchangedVariables
        val setOfCell = setOf("x", "f", "z")
        state.isNotEmpty() shouldBe true
        state shouldBe setOfCell

        eval(
            """
            private val x = "44"
            var f = 47
            """.trimIndent(),
            jupyterId = 1
        )
        state.isNotEmpty() shouldBe true
        // it's ok that there's more info, cache's data would filter out
        state shouldBe setOf("f", "x", "z")
    }

    @Test
    fun testUnchangedVariables() {
        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 1
        )
        var state = repl.notebook.unchangedVariables
        val setOfCell = setOf("x", "f", "z")
        state.isNotEmpty() shouldBe true
        state shouldBe setOfCell

        eval(
            """
            private val x = 341
            f += x
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 2
        )
        state.isEmpty() shouldBe true
        val setOfPrevCell = setOf("f")
        setOfCell shouldNotBe setOfPrevCell

        eval(
            """
            private val x = 341
            protected val z = "abcd"
            """.trimIndent(),
            jupyterId = 3
        )
        state = repl.notebook.unchangedVariables
        state.isEmpty() shouldBe true
        // assertEquals(state, setOfPrevCell)

        eval(
            """
            private val x = "abcd"
            var f = 47
            internal val z = 47
            """.trimIndent(),
            jupyterId = 4
        )
        state = repl.notebook.unchangedVariables
        state.isEmpty() shouldBe true
    }

    @Test
    fun testSerializationClearInfo() {
        eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 1
        ).metadata.evaluatedVariablesState
        repl.notebook.unchangedVariables
        eval(
            """
            val x = listOf(1, 2, 3, 4)
            """.trimIndent(),
            jupyterId = 2
        ).metadata.evaluatedVariablesState
    }
}
