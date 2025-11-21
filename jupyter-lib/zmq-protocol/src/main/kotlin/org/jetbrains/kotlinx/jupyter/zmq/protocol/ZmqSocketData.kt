package org.jetbrains.kotlinx.jupyter.zmq.protocol

import org.zeromq.SocketType
import org.zeromq.ZMQ

class ZmqSocketData(
    val name: String,
    val zmqContext: ZMQ.Context,
    val socketType: SocketType,
    val socketIdentity: ByteArray,
    val address: String,
)
