package org.jetbrains.kotlinx.jupyter.api.outputs

import org.jetbrains.kotlinx.jupyter.api.Notebook
import java.util.UUID
import kotlin.time.Duration

private fun generateId() = UUID.randomUUID().toString()

/**
 * The `Frame` class represents a single frame in an animation.
 *
 * @param delayBefore The delay duration before displaying the content of the frame.
 * @param content The content of the frame. In the end, the content is passed to
 * [DisplayHandler.handleDisplay] or [DisplayHandler.handleUpdate]
 * and handled by the dedicated display handler.
 * @param T The type of the content.
 */
class Frame<out T>(
    val delayBefore: Duration,
    val content: T,
)

/**
 * Displays all the frames left in this [frames] iterator, with respect to the delays.
 *
 * @param frames An iterator over [Frame] elements to be displayed.
 * @throws InterruptedException if any thread has interrupted the current thread
 * while the current thread is waiting for the frame delays.
 */
@Throws(InterruptedException::class)
fun Notebook.animate(frames: Iterator<Frame<*>>) {
    val displayId = generateId()
    var isFirst = true
    frames.forEachRemaining { frame ->
        val frameContent = frame.content ?: "null"
        val frameDelay = frame.delayBefore.inWholeMilliseconds
        Thread.sleep(frameDelay)
        if (isFirst) {
            display(frameContent, displayId)
            isFirst = false
        } else {
            updateDisplay(frameContent, displayId)
        }
    }
}

/**
 * Displays all the frames in this sequence, with respect to the delays.
 *
 * @param sequence A sequence of [Frame] elements to be displayed.
 */
fun Notebook.animate(sequence: Sequence<Frame<*>>) {
    animate(sequence.iterator())
}

/**
 * Displays all the frames in this iterable, with respect to the delays.
 *
 * @param iterable Iterable of [Frame] elements to be displayed.
 */
fun Notebook.animate(iterable: Iterable<Frame<*>>) {
    animate(iterable.iterator())
}

/**
 * Triggers the animation using a chain of frames, beginning with [firstFrame]
 * and subsequently obtained through the [nextFrame] function.
 *
 * @param firstFrame The first frame of the animation.
 * @param nextFrame Function that takes the current frame and returns the next frame,
 * or null if the animation is finished.
 */
fun <T> Notebook.animate(
    firstFrame: Frame<T>,
    nextFrame: (Frame<T>) -> Frame<T>?,
) {
    animate(generateSequence(firstFrame, nextFrame))
}

/**
 * Triggers the animation sequence consisting of frames that are produced by the [nextFrame] function.
 *
 * @param nextFrame A function that returns the next frame in the animation sequence.
 */
fun Notebook.animate(nextFrame: () -> Frame<*>?) {
    animate(generateSequence(nextFrame))
}

/**
 * Animates a sequence of frames by displaying them with respect to their delays.
 *
 * @param framesCount The number of frames to animate.
 * @param frameByIndex A function that maps an index to a frame.
 *                     The index represents the position of the frame in the sequence.
 */
fun Notebook.animate(
    framesCount: Int,
    frameByIndex: (index: Int) -> Frame<*>,
) {
    animate(
        sequence {
            repeat(framesCount) { frameIndex ->
                yield(frameByIndex(frameIndex))
            }
        },
    )
}

/**
 * Animates a sequence of values by displaying them with specified delay.
 * The first value is displayed without a delay.
 *
 * @param delay The delay duration between each frame.
 * @param iterator An iterator over the values to be animated.
 *
 * @throws InterruptedException if any thread is interrupted while waiting for frame delays.
 */
fun Notebook.animate(
    delay: Duration,
    iterator: Iterator<*>,
) {
    animate(
        object : Iterator<Frame<*>> {
            private var isFirst = true

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): Frame<*> {
                val content = iterator.next()
                return Frame(if (isFirst) Duration.ZERO else delay, content).also {
                    isFirst = false
                }
            }
        },
    )
}

/**
 * Animates a sequence of values with a specified delay between each frame.
 * The first value is displayed without a delay.
 *
 * @param delay The delay duration between each frame.
 * @param sequence The sequence of frames to animate.
 */
fun Notebook.animate(
    delay: Duration,
    sequence: Sequence<*>,
) {
    animate(delay, sequence.iterator())
}

/**
 * Animates a sequence of values with a specified delay between each frame.
 * The first value is displayed without a delay.
 *
 * @param delay The delay duration between each frame.
 * @param nextValue A function that provides the next value in the sequence.
 */
fun Notebook.animate(
    delay: Duration,
    nextValue: () -> Any?,
) {
    animate(delay, generateSequence(nextValue))
}

/**
 * Animates a sequence of values with a specified delay between each frame.
 * The first value is displayed without a delay.
 *
 * @param delay The delay duration between each frame.
 * @param firstValue The initial value of the sequence.
 * @param nextValue A function that generates the next value based on the current value in the sequence.
 * @param T The type of the values in the sequence.
 */
fun <T : Any> Notebook.animate(
    delay: Duration,
    firstValue: T?,
    nextValue: (T) -> T?,
) {
    animate(delay, generateSequence(firstValue, nextValue))
}

/**
 * Animates an iterable sequence of values, each one (except first) appearing after a specified delay.
 *
 * @param delay The time delay to wait before displaying each frame.
 * @param iterable The set of elements to display as animation frames.
 */
fun Notebook.animate(
    delay: Duration,
    iterable: Iterable<*>,
) {
    animate(delay, iterable.iterator())
}

/**
 * Animates a sequence of values with a specified delay between each frame.
 * The first value is displayed without a delay.
 *
 * @param framesCount The number of frames to animate.
 * @param delay The delay duration between each frame.
 * @param valueByIndex The function that generates the content of each frame based on the frame index.
 */
fun Notebook.animate(
    delay: Duration,
    framesCount: Int,
    valueByIndex: (index: Int) -> Any?,
) {
    animate(
        delay,
        sequence {
            repeat(framesCount) { frameIndex ->
                yield(valueByIndex(frameIndex))
            }
        },
    )
}
