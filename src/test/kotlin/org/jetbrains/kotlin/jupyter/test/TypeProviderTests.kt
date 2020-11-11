package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.receivers.TypeProviderReceiver
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlin.jupyter.ReplCompilerException
import org.jetbrains.kotlin.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlin.jupyter.ResolverConfig
import org.jetbrains.kotlin.jupyter.config.defaultRepositories
import org.jetbrains.kotlin.jupyter.libraries.LibraryFactory
import org.jetbrains.kotlin.jupyter.libraries.parseLibraryDescriptors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class TypeProviderTests {

    @Test
    fun test() {
        val descriptor =
            """
            {
                "typeConverters": {
                    "kotlin.collections.List<kotlin.Int>": "generateCode(${'$'}it)"
                }
            }
            """.trimIndent()
        val cp = classpath + File(TypeProviderReceiver::class.java.protectionDomain.codeSource.location.toURI().path)
        val libJsons = mapOf("mylib" to Json.decodeFromString<JsonObject>(descriptor))
        val libraryFactory = LibraryFactory.EMPTY
        val config = ResolverConfig(defaultRepositories, libraryFactory.getResolverFromNamesMap(parseLibraryDescriptors(libJsons)))
        val repl = ReplForJupyterImpl(libraryFactory, cp, null, config, scriptReceivers = listOf(TypeProviderReceiver()))

        // create list 'l' of size 3
        val code1 =
            """
            %use mylib
            val l = listOf(1,2,3)
            """.trimIndent()
        repl.eval(code1)
        assertEquals(3, repl.eval("l.value2").resultValue)

        // create list 'q' of the same size 3
        repl.eval("val q = l.asReversed()")
        assertEquals(1, repl.eval("q.value2").resultValue)

        // check that 'l' and 'q' have the same types
        assertEquals(
            3,
            repl.eval(
                """var a = l
            a = q
            a.value0
        """.trimMargin()
            ).resultValue
        )

        // create a list of size 6
        repl.eval("val w = l + a")
        assertEquals(3, repl.eval("w.value3").resultValue)

        // check that 'value3' is not available for list 'l'
        assertThrows<ReplCompilerException> {
            repl.eval("l.value3")
        }
    }
}
