package org.jetbrains.kotlinx.jupyter.protocol.startup

import org.jetbrains.kotlinx.jupyter.protocol.exceptions.tryFinally
import java.io.Closeable
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object GeneratedPortsHolder {
    private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun addPort(port: Int): Boolean = (port !in usedPorts) && isPortAvailable(port) && usedPorts.add(port)
}

fun isPortAvailable(port: Int): Boolean =
    listOf(
        {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(null as InetAddress?, port))
            }
        },
        {
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(null as InetAddress?, port))
            }
        },
    ).all { checkSocketIsFree(it) }

private fun checkSocketIsFree(socketFactory: () -> Closeable): Boolean {
    var socket: Closeable? = null
    return tryFinally(
        action = {
            try {
                socket = socketFactory()
                true
            } catch (_: IOException) {
                false
            }
        },
        finally = { socket?.close() },
    )
}

fun randomIntsInRange(
    rangeStart: Int,
    rangeEnd: Int,
    limit: Int = rangeEnd - rangeStart,
): Sequence<Int> = generateSequence { Random.nextInt(rangeStart, rangeEnd) }.take(limit)

class PortsGenerator(
    private val portsToTry: () -> Sequence<Int>,
) {
    fun randomPort() =
        portsToTry().find {
            GeneratedPortsHolder.addPort(it)
        } ?: throw RuntimeException("No free port found")

    companion object
}

fun PortsGenerator.Companion.create(
    portRangeStart: Int,
    portRangeEnd: Int,
) = PortsGenerator {
    randomIntsInRange(portRangeStart, portRangeEnd)
}
