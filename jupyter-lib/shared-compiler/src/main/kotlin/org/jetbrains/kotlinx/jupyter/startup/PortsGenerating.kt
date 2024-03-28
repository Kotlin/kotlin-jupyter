package org.jetbrains.kotlinx.jupyter.startup

import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object GeneratedPortsHolder {
    private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private fun isPortAvailable(port: Int): Boolean {
        var tcpSocket: ServerSocket? = null
        var udpSocket: DatagramSocket? = null
        try {
            tcpSocket = ServerSocket(port)
            tcpSocket.reuseAddress = true
            udpSocket = DatagramSocket(port)
            udpSocket.reuseAddress = true
            return true
        } catch (_: IOException) {
        } finally {
            tcpSocket?.close()
            udpSocket?.close()
        }
        return false
    }

    fun addPort(port: Int): Boolean = (port !in usedPorts) && isPortAvailable(port) && usedPorts.add(port)
}

fun randomIntsInRange(rangeStart: Int, rangeEnd: Int, limit: Int = rangeEnd - rangeStart): Sequence<Int> {
    return generateSequence { Random.nextInt(rangeStart, rangeEnd) }.take(limit)
}

class PortsGenerator(
    private val portsToTry: () -> Sequence<Int>,
) {
    fun randomPort() =
        portsToTry().find {
            GeneratedPortsHolder.addPort(it)
        } ?: throw RuntimeException("No free port found")

    companion object
}

fun PortsGenerator.Companion.create(portRangeStart: Int, portRangeEnd: Int) = PortsGenerator { randomIntsInRange(portRangeStart, portRangeEnd) }

fun createRandomKernelPorts() = PortsGenerator.create(32768, 65536)
    .let { generator -> createKernelPorts { generator.randomPort() } }
