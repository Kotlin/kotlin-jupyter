package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.jupyter.api.InMemoryMimeTypes
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResultEx
import org.jetbrains.kotlinx.jupyter.api.takeScreenshot
import org.jetbrains.kotlinx.jupyter.protocol.api.EMPTY
import org.jetbrains.kotlinx.jupyter.test.repl.AbstractSingleReplTest
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.reflect.KClass
import kotlin.test.DefaultAsserter.fail
import kotlin.test.assertEquals

/**
 * Class responsible for testing how in-memory results are handled by the embedded kernel.
 */
class InMemoryResultTests : AbstractSingleReplTest() {
    companion object {
        // Lock to prevent multiple tests accessing the screen at the same time. It looks like if multiple
        // tests are opening JFrame/JDialogs, then taking screenshots can fail with
        // "Cannot invoke "java.awt.Window.isOpaque()" because "w" is null", if they overlap.
        const val SCREEN_LOCK = "screenLock"
    }

    override val repl = makeEmbeddedRepl()

    // Skip tests always for now as they are unstable
    private val skipGraphicsTests = true // GraphicsEnvironment.isHeadless()

    private fun assumeGraphicsSupported() {
        Assumptions.assumeFalse(skipGraphicsTests, "Test is skipped: graphics is disabled")
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testJFrame() {
        assumeGraphicsSupported()
        doInMemoryTest(
            JFrame::class,
            """
            import javax.swing.JFrame
            val frame = JFrame("panel")
            frame.setSize(300, 300)
            frame.isVisible = true
            frame
            """.trimIndent(),
        )
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testJDialog() {
        assumeGraphicsSupported()
        doInMemoryTest(
            JDialog::class,
            """
            import javax.swing.JDialog
            val dialog = JDialog()
            dialog.setSize(300, 300)
            dialog.isVisible = true
            dialog
            """.trimIndent(),
        )
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testJComponent() {
        doInMemoryTest(
            JComponent::class,
            """
            import javax.swing.JPanel
            val panel = JPanel()
            panel.setSize(300, 300)
            panel
            """.trimIndent(),
        )
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testScreenshotWithNoSize() {
        val panel = JPanel()
        assertNull(panel.takeScreenshot())
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testScreenshotOfJFrame() {
        assumeGraphicsSupported()
        val frame = JFrame()
        frame.size = Dimension(100, 50)
        val button = JButton("Button 1")
        frame.contentPane.add(button)
        frame.isVisible = true
        val screenshot = frame.takeScreenshot()
        assertNotEmptyImage(screenshot)
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testScreenshotOfJDialog() {
        assumeGraphicsSupported()
        val dialog = JDialog()
        dialog.size = Dimension(100, 50)
        val button = JButton("Button 1")
        dialog.contentPane.add(button)
        dialog.isVisible = true
        val screenshot = dialog.takeScreenshot()
        assertNotEmptyImage(screenshot)
    }

    @Test
    @ResourceLock(SCREEN_LOCK)
    fun testScreenshotOfJComponent() {
        val panel = JPanel()
        panel.size = Dimension(100, 50)
        val button = JButton("Button 1")
        button.size = Dimension(100, 50)
        panel.add(button)
        val screenshot = panel.takeScreenshot()
        assertNotEmptyImage(screenshot)
    }

    private fun doInMemoryTest(
        expectedOutputClass: KClass<*>,
        code: String,
    ) {
        val result = eval(code)
        result.renderedValue.shouldBeInstanceOf<MimeTypedResultEx>()
        result.displayValue.shouldBeInstanceOf<MimeTypedResultEx>()
        val displayObj = result.displayValue as MimeTypedResultEx
        val displayDataJson = displayObj.toJson(Json.EMPTY, null)
        val displayData = displayDataJson["data"] as JsonObject
        displayData.size.shouldBe(2)
        displayData.shouldContainKey("image/png")
        displayData.shouldContainKey(InMemoryMimeTypes.SWING)
        assertEquals("-1", (displayData[InMemoryMimeTypes.SWING] as JsonPrimitive).content)
        val inMemHolder = repl.notebook.sharedReplContext!!.inMemoryReplResultsHolder
        inMemHolder.size.shouldBe(1)
        inMemHolder.getReplResult("-1").shouldBeInstanceOf(expectedOutputClass)
    }

    // Check if a screenshot actually contains anything useful.
    // We assume "useful" means an image with width/length > 0 and doesn't only consist of
    // one color.
    private fun assertNotEmptyImage(image: BufferedImage?) {
        if (image == null) {
            fail("`null` image was returned")
        }
        assertNotEquals(0, image.width)
        assertNotEquals(0, image.height)
        val topLeftColor = image.getRGB(0, 0)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                if (image.getRGB(x, y) != topLeftColor) {
                    return
                }
            }
        }
        fail("Image only contained a single color: $topLeftColor")
    }
}
