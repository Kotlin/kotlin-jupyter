package org.jetbrains.kotlinx.jupyter.zmq.protocol

import java.util.UUID

fun generateZmqIdentity() = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
