package org.jetbrains.kotlinx.jupyter.test.repl

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.ReplForJupyterImpl
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.Renderable
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.dependencies.ResolverConfig
import org.jetbrains.kotlinx.jupyter.libraries.GitHubRepoName
import org.jetbrains.kotlinx.jupyter.libraries.GitHubRepoOwner
import org.jetbrains.kotlinx.jupyter.libraries.LibrariesDir
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorExt
import org.jetbrains.kotlinx.jupyter.libraries.LibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.LocalSettingsPath
import org.jetbrains.kotlinx.jupyter.libraries.ResolutionInfoProvider
import org.jetbrains.kotlinx.jupyter.libraries.getStandardResolver
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.classpath
import org.jetbrains.kotlinx.jupyter.test.standardResolverRuntimeProperties
import org.jetbrains.kotlinx.jupyter.test.testResolverConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithResolverTests : AbstractReplTest() {
    private val repl = ReplForJupyterImpl(resolutionInfoProvider, classpath, homeDir, resolverConfig)

    private fun getReplWithStandardResolver(): ReplForJupyterImpl {
        val standardResolutionInfoProvider = ResolutionInfoProvider.withDefaultDirectoryResolution(homeDir.resolve(LibrariesDir))
        val config = ResolverConfig(defaultRepositories, getStandardResolver(".", standardResolutionInfoProvider))
        return ReplForJupyterImpl(standardResolutionInfoProvider, classpath, homeDir, config, standardResolverRuntimeProperties)
    }

    @Test
    fun testLetsPlot() {
        val code1 = "%use lets-plot"
        val code2 =
            """lets_plot(mapOf<String, Any>("cat" to listOf("a", "b")))"""
        val displays = mutableListOf<Any>()
        val displayHandler = TestDisplayHandler(displays)

        val res1 = repl.eval(code1, displayHandler)
        assertEquals(1, displays.count())
        displays.clear()
        assertNull(res1.resultValue)
        val res2 = repl.eval(code2, displayHandler)
        assertEquals(0, displays.count())
        val mime = res2.resultValue as? MimeTypedResult
        assertNotNull(mime)
        assertEquals(1, mime.size)
        assertEquals("text/html", mime.entries.first().key)
        assertNotNull(res2.resultValue)
    }

    @Test
    @Disabled // TODO: restore
    fun testDataframe() {
        val res = repl.eval(
            """
            %use dataframe
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            ).typed<Unit>()
            """.trimIndent()
        )

        val value = res.resultValue
        assertTrue(value is MimeTypedResult)

        val html = value["text/html"]!!
        assertTrue(html.contains("Bill"))
    }

    @Test
    fun testSerialization() {
        val serialized = repl.eval(
            """
            %use serialization
            
            @Serializable
            class C(val x: Int)
            
            Json.encodeToString(C(42))
            """.trimIndent()
        )

        assertEquals("""{"x":42}""", serialized.resultValue)
    }

    @Test
    fun testLibraryFromClasspath() {
        repl.eval(
            """
            @file:Repository("https://dl.bintray.com/ileasile/kotlin-datascience-ileasile")
            @file:DependsOn("org.jetbrains.test.kotlinx.jupyter.api:notebook-api-test:0.0.15")
            """.trimIndent()
        )

        val res = repl.eval(
            """
            ses.visualizeColor("red")
            """.trimIndent()
        )

        val result = res.resultValue as Renderable
        val json = result.render(repl.notebook).toJson()
        val jsonData = json["data"] as JsonObject
        val htmlString = jsonData["text/html"] as JsonPrimitive
        assertEquals("""<span style="color:red">red</span>""", htmlString.content)
    }

    @Test
    fun testResolverRepoOrder() {
        val repl = getReplWithStandardResolver()

        val res = repl.eval(
            """
            @file:Repository("https://repo.osgeo.org/repository/release/")
            @file:DependsOn("org.geotools:gt-shapefile:[23,)")
            @file:DependsOn("org.geotools:gt-cql:[23,)")
            
            %use lets-plot@f98400094c0650d3497f3fda9910dd86705ee655(api=1.1.0)
            
            @file:DependsOn("org.jetbrains.lets-plot-kotlin:lets-plot-kotlin-geotools:1.1.0")
            
            import jetbrains.letsPlot.toolkit.geotools.toSpatialDataset
            """.trimIndent()
        )

        Assertions.assertTrue(res.newClasspath.size >= 2)
    }

    @Test
    fun testTwoLibrariesInUse() {
        val code = "%use lets-plot, krangl"
        val displays = mutableListOf<Any>()
        val displayHandler = TestDisplayHandler(displays)

        repl.eval(code, displayHandler)
        assertEquals(1, displays.count())
    }

    @Test
    fun testKranglImportInfixFun() {
        repl.eval("""%use krangl, lets-plot""")
        val res = repl.eval(""" "a" to {it["a"]} """)
        assertNotNull(res.resultValue)
    }

    @Test
    fun testStandardLibraryResolver() {
        val repl = getReplWithStandardResolver()

        val res = repl.eval(
            """
            %use krangl(0.13)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
            """.trimIndent()
        )
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testDefaultInfoSwitcher() {
        val repl = getReplWithStandardResolver()
        val infoProvider = repl.resolutionInfoProvider

        val initialDefaultResolutionInfo = infoProvider.fallback
        Assertions.assertTrue(initialDefaultResolutionInfo is LibraryResolutionInfo.ByDir)

        repl.eval("%useLatestDescriptors")
        Assertions.assertTrue(infoProvider.fallback is LibraryResolutionInfo.ByGitRef)

        repl.eval("%useLatestDescriptors -off")
        Assertions.assertTrue(infoProvider.fallback === initialDefaultResolutionInfo)
    }

    @Test
    fun testUseFileUrlRef() {
        val repl = getReplWithStandardResolver()

        val commit = "561ce1a324a9434d3481456b11678851b48a3132"
        val libraryPath = "src/test/testData/test-init.json"

        val res1 = repl.eval(
            """
            %use @file[$libraryPath](name=x, value=42)
            x
            """.trimIndent()
        )
        assertEquals(42, res1.resultValue)

        val res2 = repl.eval(
            """
            %use @url[https://raw.githubusercontent.com/$GitHubRepoOwner/$GitHubRepoName/$commit/$libraryPath](name=y, value=43)
            y
            """.trimIndent()
        )
        assertEquals(43, res2.resultValue)

        val displays = mutableListOf<Any>()
        val handler = TestDisplayHandler(displays)

        val res3 = repl.eval("%use lets-plot@$commit", handler)
        assertEquals(1, displays.count())
        assertNull(res3.resultValue)
        displays.clear()

        val res4 = repl.eval(
            """
            %use @$libraryPath(name=z, value=44)
            z
            """.trimIndent()
        )
        assertEquals(44, res4.resultValue)
    }

    @Test
    fun testLocalLibrariesStorage() {
        @Language("json")
        val descriptorText = """
            {
              "init": [
                "val y = 25"
              ]
            }
        """.trimIndent()

        val libName = "test-local"
        val file = LocalSettingsPath.resolve(LibrariesDir).resolve("$libName.$LibraryDescriptorExt").toFile()
        file.delete()

        file.parentFile.mkdirs()
        file.writeText(descriptorText)

        val repl = getReplWithStandardResolver()
        val result = repl.eval(
            """
            %use $libName
            y
            """.trimIndent()
        )

        assertEquals(25, result.resultValue)
        file.delete()
    }

    @Test
    fun testRuntimeDepsResolution() {
        val res = repl.eval(
            """
            %use krangl(0.13)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
            """.trimIndent()
        )
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testNullableErasure() {
        val code1 = "val a: Int? = 3"
        repl.eval(code1)
        val code2 = "a+2"
        val res = repl.eval(code2).resultValue
        assertEquals(5, res)
    }

    @Test
    fun testKlaxonClasspathDoesntLeak() {
        val res = repl.eval(
            """
            %use klaxon(2.1.8)
            class Person (val name: String, var age: Int = 23)
            val klaxon = Klaxon()
            val parseRes = klaxon.parse<Person>(""${'"'}
                {
                  "name": "John Smith"
                }
                ""${'"'})
            parseRes?.age
            """.trimIndent()
        )
        assertEquals(23, res.resultValue)
    }

    companion object {
        val resolverConfig = testResolverConfig
    }
}
