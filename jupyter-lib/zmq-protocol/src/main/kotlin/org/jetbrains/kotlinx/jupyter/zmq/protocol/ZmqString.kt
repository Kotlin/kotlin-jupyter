package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.zeromq.ZMQ

object ZmqString {
    fun getBytes(string: String) = string.toByteArray(ZMQ.CHARSET)

    fun getString(bytes: ByteArray) = String(bytes, ZMQ.CHARSET)
}
