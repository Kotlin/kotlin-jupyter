package org.jetbrains.kotlinx.jupyter.startup

import java.io.Closeable
import java.io.IOException
import java.net.DatagramSocket
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

object GeneratedPortsHolder {
    private val usedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun addPort(port: Int): Boolean = (port !in usedPorts) && isPortAvailable(port) && usedPorts.add(port)
}

fun isPortAvailable(port: Int): Boolean =
    listOf(
        { ServerSocket(port).apply { reuseAddress = true } },
        { DatagramSocket(port).apply { reuseAddress = true } },
    ).all { checkSocketIsFree(it) }

private fun checkSocketIsFree(socketFactory: () -> Closeable): Boolean {
    var socket: Closeable? = null
    return try {
        socket = socketFactory()
        true
    } catch (_: IOException) {
        false
    } finally {
        socket?.close()
    }
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

fun createRandomKernelPorts() =
    PortsGenerator
        .create(32768, 65536)
        .let { generator -> createKernelPorts { generator.randomPort() } }
