package org.jetbrains.kotlinx.jupyter.test.repl

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.ir.types.IdSignatureValues.result
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.api.createRenderer
import org.jetbrains.kotlinx.jupyter.api.libraries.createLibrary
import org.jetbrains.kotlinx.jupyter.startup.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.test.TestDisplayHandlerWithRendering
import org.jetbrains.kotlinx.jupyter.test.evalEx
import org.jetbrains.kotlinx.jupyter.test.rawValue
import org.jetbrains.kotlinx.jupyter.test.renderedValue
import org.jetbrains.kotlinx.jupyter.test.shouldBeText
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Ignore

@Execution(ExecutionMode.SAME_THREAD)
class ReplWithStandardResolverAndRenderingTests : AbstractSingleReplTest() {
    private val displays get() = repl.notebook.displays.getAll()

    override val repl =
        makeReplWithStandardResolver { notebook ->
            TestDisplayHandlerWithRendering(notebook)
        }

    @Test
    fun testDataframeDisplay() {
        eval("SessionOptions.resolveSources = true", 1, false)

        eval(
            """
            %use dataframe(0.10.0-dev-1373)
            
            val name by column<String>()
            val height by column<Int>()
            
            dataFrameOf(name, height)(
                "Bill", 135,
                "Mark", 160
            )
            """.trimIndent(),
            2,
            true,
        )

        val declaredProperties =
            repl.notebook.currentCell!!.declarations
                .filter { it.kind == DeclarationKind.PROPERTY }
                .mapNotNull { it.name }

        when (repl.compilerMode) {
            ReplCompilerMode.K1 -> {
                declaredProperties shouldBe listOf("name", "height")
            }
            ReplCompilerMode.K2 -> {
                // Wait for https://youtrack.jetbrains.com/issue/KT-75580/K2-Repl-Cannot-access-snippet-properties-using-Kotlin-reflection
                declaredProperties shouldBe listOf()
            }
        }

        eval(
            """DISPLAY((Out[2] as DataFrame<*>).filter { it.index() >= 0 && it.index() <= 10 }, "")""",
            3,
            false,
        )
    }

    @Test
    fun `display inside renderer works as expected`() {
        repl.eval {
            addLibrary(
                createLibrary(repl.notebook) {
                    addRenderer(
                        createRenderer(
                            { it.value is Int },
                            { host, field -> host.display("${field.value}: hi from host.display", null) },
                        ),
                    )
                },
            )
        }

        val result = repl.evalEx("42")
        result.renderedValue shouldBe Unit
        result.rawValue shouldBe 42

        val displayResult = displays.single()

        displayResult.shouldBeText() shouldBe "42: hi from host.display"
    }

    @Ignore
    @Test
    fun `display compose screenshots`() {
        val result =
            repl.evalEx(
                """
                @file:Repository("https://maven.google.com/")
                @file:DependsOn("org.jetbrains.compose:compose-full:1.7.3")
                @file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
                @file:DependsOn("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.8.18")
                @file:DependsOn("org.jetbrains.skiko:skiko-awt:0.8.18")
                @file:DependsOn("androidx.collection:collection-jvm:1.4.5")
                @file:DependsOn("androidx.lifecycle:lifecycle-common:2.8.7")
                @file:DependsOn("androidx.lifecycle:lifecycle-viewmodel-compose-desktop:2.8.4")
                @file:DependsOn("androidx.lifecycle:lifecycle-runtime-compose-desktop:2.8.4")
                
                import androidx.compose.foundation.background
                import androidx.compose.foundation.layout.Box
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.foundation.layout.size
                import androidx.compose.material.Button
                import androidx.compose.material.MaterialTheme
                import androidx.compose.material.Text
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.getValue
                import androidx.compose.runtime.mutableStateOf
                import androidx.compose.runtime.remember
                import androidx.compose.runtime.setValue
                import androidx.compose.ui.Alignment
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.graphics.Color
                import androidx.compose.ui.unit.DpSize
                import androidx.compose.ui.unit.dp
                import androidx.compose.ui.window.Window
                import androidx.compose.ui.window.application
                import androidx.compose.ui.window.rememberWindowState
                
                COMPOSE {
                    val COLORS = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow)
                    var colorIndex by remember { mutableStateOf(0) }
                    val color = COLORS[colorIndex]
                    Box(modifier = Modifier.fillMaxSize().background(color).size(200.dp, 200.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = {
                            colorIndex = (colorIndex + 1) % COLORS.size
                        }) {
                            Text("Click Me!")
                        }
                    }
                }
                """.trimIndent(),
            )
        result.renderedValue shouldBe Unit
    }
}
