package org.jetbrains.kotlinx.jupyter.streams

import java.io.IOException
import java.io.InputStream

object DisabledStdinInputStream : InputStream() {
    override fun read(): Int = throw IOException("Input from stdin is unsupported by the client")
}
