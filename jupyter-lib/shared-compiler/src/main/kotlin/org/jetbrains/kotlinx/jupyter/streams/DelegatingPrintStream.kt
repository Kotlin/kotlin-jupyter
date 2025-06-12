package org.jetbrains.kotlinx.jupyter.streams

import java.io.PrintStream
import java.util.Locale

class DelegatingPrintStream(
    private val getDelegate: () -> PrintStream,
) : PrintStream(nullOutputStream()) {
    override fun equals(other: Any?): Boolean = getDelegate() == other

    override fun hashCode(): Int = getDelegate().hashCode()

    override fun toString(): String = getDelegate().toString()

    override fun close() = getDelegate().close()

    override fun flush() = getDelegate().flush()

    override fun write(b: Int) = getDelegate().write(b)

    override fun write(
        buf: ByteArray,
        off: Int,
        len: Int,
    ) = getDelegate().write(buf, off, len)

    override fun write(buf: ByteArray) = getDelegate().write(buf)

    override fun append(csq: CharSequence?): PrintStream = getDelegate().append(csq)

    override fun append(
        csq: CharSequence?,
        start: Int,
        end: Int,
    ): PrintStream = getDelegate().append(csq, start, end)

    override fun append(c: Char): PrintStream = getDelegate().append(c)

    override fun checkError(): Boolean = getDelegate().checkError()

    override fun print(b: Boolean) = getDelegate().print(b)

    override fun print(c: Char) = getDelegate().print(c)

    override fun print(i: Int) = getDelegate().print(i)

    override fun print(l: Long) = getDelegate().print(l)

    override fun print(f: Float) = getDelegate().print(f)

    override fun print(d: Double) = getDelegate().print(d)

    override fun print(s: CharArray) = getDelegate().print(s)

    override fun print(s: String?) = getDelegate().print(s)

    override fun print(obj: Any?) = getDelegate().print(obj)

    override fun println() = getDelegate().println()

    override fun println(x: Boolean) = getDelegate().println(x)

    override fun println(x: Char) = getDelegate().println(x)

    override fun println(x: Int) = getDelegate().println(x)

    override fun println(x: Long) = getDelegate().println(x)

    override fun println(x: Float) = getDelegate().println(x)

    override fun println(x: Double) = getDelegate().println(x)

    override fun println(x: CharArray) = getDelegate().println(x)

    override fun println(x: String?) = getDelegate().println(x)

    override fun println(x: Any?) = getDelegate().println(x)

    override fun printf(
        format: String,
        vararg args: Any?,
    ): PrintStream = getDelegate().printf(format, *args)

    override fun printf(
        l: Locale?,
        format: String,
        vararg args: Any?,
    ): PrintStream = getDelegate().printf(l, format, *args)

    override fun format(
        format: String,
        vararg args: Any?,
    ): PrintStream = getDelegate().format(format, *args)

    override fun format(
        l: Locale?,
        format: String,
        vararg args: Any?,
    ): PrintStream = getDelegate().format(l, format, *args)
}
