package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionRequest
import org.jetbrains.kotlinx.jupyter.libraries.AbstractLibraryResolutionInfo
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandler
import org.jetbrains.kotlinx.jupyter.test.assertUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithStandardResolverTests : AbstractSingleReplTest() {
    override val repl = makeReplWithStandardResolver()

    @Test
    fun testResolverRepoOrder() {
        val res = eval(
            """
            @file:Repository("https://repo.osgeo.org/repository/release/")
            @file:DependsOn("org.geotools:gt-shapefile:[23,)")
            @file:DependsOn("org.geotools:gt-cql:[23,)")
            
            %use lets-plot@f2bb7075b316e7181ff8fddb1e045c4ed2c26442(api=2.0.1)
            
            @file:DependsOn("org.jetbrains.lets-plot:lets-plot-kotlin-geotools:2.0.1")
            
            import jetbrains.letsPlot.toolkit.geotools.toSpatialDataset
            """.trimIndent()
        )

        Assertions.assertTrue(res.metadata.newClasspath.size >= 2)
    }

    @Test
    fun testStandardLibraryResolver() {
        val res = eval(
            """
            %use krangl@d91d045946f59(0.16.2)
            val df = DataFrame.readCSV("src/test/testData/resolve-with-runtime.csv")
            df.head().rows.first().let { it["name"].toString() + " " + it["surname"].toString() }
            """.trimIndent()
        )
        assertEquals("John Smith", res.resultValue)
    }

    @Test
    fun testDefaultInfoSwitcher() {
        val infoProvider = repl.resolutionInfoProvider

        val initialDefaultResolutionInfo = infoProvider.fallback
        Assertions.assertTrue(initialDefaultResolutionInfo is AbstractLibraryResolutionInfo.ByDir)

        eval("%useLatestDescriptors")
        Assertions.assertTrue(infoProvider.fallback is AbstractLibraryResolutionInfo.ByGitRef)

        eval("%useLatestDescriptors off")
        Assertions.assertTrue(infoProvider.fallback === initialDefaultResolutionInfo)
    }

    @Test
    fun testUseFileUrlRef() {
        val commit = "cfcf8257116ad3753b176a9f779eaaea4619dacd"
        val libsCommit = "f2bb7075b316e7181ff8fddb1e045c4ed2c26442"
        val libraryPath = "src/test/testData/test-init.json"

        val res1 = eval(
            """
            %use @file[$libraryPath](name=x, value=42)
            x
            """.trimIndent()
        )
        assertEquals(42, res1.resultValue)

        val res2 = eval(
            """
            %use @url[https://raw.githubusercontent.com/Kotlin/kotlin-jupyter/$commit/$libraryPath](name=y, value=43)
            y
            """.trimIndent()
        )
        assertEquals(43, res2.resultValue)

        val displays = mutableListOf<Any>()
        val handler = TestDisplayHandler(displays)

        val res3 = eval("%use lets-plot@$libsCommit", handler)
        assertEquals(1, displays.count())
        assertUnit(res3.resultValue)
        displays.clear()

        val res4 = eval(
            """
            %use @$libraryPath(name=z, value=44)
            z
            """.trimIndent()
        )
        assertEquals(44, res4.resultValue)
    }

    @Test
    fun testHttpRedirection() {
        val res = eval(
            """
            %use jep@url[https://github.com/hanslovsky/jepyter/releases/download/jepyter-0.1.8/jep.json]
            1
            """.trimIndent()
        ).resultValue
        assertEquals(1, res)
    }

    @Test
    fun testLibraryRequestsRecording() {
        eval("%use default")
        val res = eval("notebook.libraryRequests").resultValue

        res.shouldBeInstanceOf<List<LibraryResolutionRequest>>()
        res.shouldHaveSize(3)

        val expectedLibs = listOf("default", "dataframe", "lets-plot-dataframe")
        for (i in res.indices) {
            res[i].reference.name shouldBe expectedLibs[i]
            res[i].definition.originalDescriptorText.shouldNotBeBlank()
        }
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
        val file = KERNEL_LIBRARIES.userLibrariesDir.resolve(KERNEL_LIBRARIES.descriptorFileName(libName))
        file.delete()

        file.parentFile.mkdirs()
        file.writeText(descriptorText)

        val result = eval(
            """
            %use $libName
            y
            """.trimIndent()
        )

        assertEquals(25, result.resultValue)
        file.delete()
    }
}
