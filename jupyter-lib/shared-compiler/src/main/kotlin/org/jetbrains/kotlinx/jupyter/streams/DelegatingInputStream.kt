package org.jetbrains.kotlinx.jupyter.streams

import java.io.InputStream
import java.io.OutputStream

class DelegatingInputStream(
    val getDelegate: () -> InputStream,
) : InputStream() {
    override fun read(): Int = getDelegate().read()

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = getDelegate().read(b, off, len)

    override fun read(b: ByteArray): Int = getDelegate().read(b)

    override fun readNBytes(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int = getDelegate().readNBytes(b, off, len)

    override fun readNBytes(len: Int): ByteArray = getDelegate().readNBytes(len)

    override fun readAllBytes(): ByteArray = getDelegate().readAllBytes()

    override fun skip(n: Long): Long = getDelegate().skip(n)

    override fun available(): Int = getDelegate().available()

    override fun mark(readlimit: Int) = getDelegate().mark(readlimit)

    override fun reset() = getDelegate().reset()

    override fun markSupported() = getDelegate().markSupported()

    override fun transferTo(out: OutputStream) = getDelegate().transferTo(out)

    override fun close(): Unit = getDelegate().close()

    override fun equals(other: Any?): Boolean = getDelegate() == other

    override fun hashCode(): Int = getDelegate().hashCode()

    override fun toString(): String = getDelegate().toString()
}
