@file:Suppress("FunctionName", "unused")

package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.outputs.Frame
import org.jetbrains.kotlinx.jupyter.api.outputs.animate
import java.util.UUID
import kotlin.time.Duration

private fun generateId() = UUID.randomUUID().toString()

/**
 * @see Notebook.animate
 */
@Throws(InterruptedException::class)
fun ScriptTemplateWithDisplayHelpers.ANIMATE(frames: Iterator<Frame<*>>) {
    notebook.animate(frames)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(sequence: Sequence<Frame<*>>) {
    notebook.animate(sequence)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(iterable: Iterable<Frame<*>>) {
    notebook.animate(iterable)
}

/**
 * @see Notebook.animate
 */
fun <T> ScriptTemplateWithDisplayHelpers.ANIMATE(
    firstFrame: Frame<T>,
    nextFrame: (Frame<T>) -> Frame<T>?,
) {
    notebook.animate(firstFrame, nextFrame)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(nextFrame: () -> Frame<*>?) {
    notebook.animate(nextFrame)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    framesCount: Int,
    frameByIndex: (index: Int) -> Frame<*>,
) {
    notebook.animate(framesCount, frameByIndex)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    iterator: Iterator<*>,
) {
    notebook.animate(delay, iterator)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    sequence: Sequence<*>,
) {
    notebook.animate(delay, sequence)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    nextValue: () -> Any?,
) {
    notebook.animate(delay, nextValue)
}

/**
 * @see Notebook.animate
 */
fun <T : Any> ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    firstValue: T?,
    nextValue: (T) -> T?,
) {
    notebook.animate(delay, firstValue, nextValue)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    iterable: Iterable<*>,
) {
    notebook.animate(delay, iterable)
}

/**
 * @see Notebook.animate
 */
fun ScriptTemplateWithDisplayHelpers.ANIMATE(
    delay: Duration,
    framesCount: Int,
    valueByIndex: (index: Int) -> Any?,
) {
    notebook.animate(delay, framesCount, valueByIndex)
}
