package org.jetbrains.kotlinx.jupyter.util

import java.io.Closeable
import kotlin.reflect.KMutableProperty0

var systemErrStream
    get() = System.err
    set(value) {
        System.setErr(value)
    }

var systemOutStream
    get() = System.out
    set(value) {
        System.setOut(value)
    }

var systemInStream
    get() = System.`in`
    set(value) {
        System.setIn(value)
    }

fun <StreamT : Closeable, R> withSubstitutedStream(
    standardStreamProp: KMutableProperty0<StreamT>,
    newStreamFactory: (StreamT) -> StreamT,
    body: (StreamT) -> R,
): R {
    val originalStream = standardStreamProp.get()
    val newStream = newStreamFactory(originalStream)
    return try {
        standardStreamProp.set(newStream)
        body(newStream)
    } finally {
        newStream.close()
        standardStreamProp.set(originalStream)
    }
}
