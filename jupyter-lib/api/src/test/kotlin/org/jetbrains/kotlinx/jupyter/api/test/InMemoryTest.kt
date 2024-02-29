package org.jetbrains.kotlinx.jupyter.api.test

import org.jetbrains.kotlinx.jupyter.api.takeScreenshot
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.test.assertNotNull

class InMemoryTest {

    @Test
    fun testScreenshotOfJFrame() {
        val frame = JFrame("Color Changer")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.preferredSize = Dimension(300, 200)
        val panel = JPanel()
        val button1 = JButton("Button 1")
        val button2 = JButton("Button 2")
        val button3 = JButton("Button 3")
        button1.addActionListener { panel.background = Color.RED }
        button2.addActionListener { panel.background = Color.GREEN }
        button3.addActionListener { panel.background = Color.BLUE }
        panel.add(button1)
        panel.add(button2)
        panel.add(button3)
        frame.add(panel)
        frame.pack()
        val screenshot = panel.takeScreenshot()
        assertNotNull(screenshot)
    }
}