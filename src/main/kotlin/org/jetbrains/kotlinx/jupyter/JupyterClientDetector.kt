package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.JupyterClientType

object JupyterClientDetector {
    fun detect(): JupyterClientType {
        return try {
            doDetect()
        } catch (e: LinkageError) {
            log.error("Unable to detect Jupyter client type because of incompatible JVM version", e)
            JupyterClientType.UNKNOWN
        }
    }

    private fun doDetect(): JupyterClientType {
        log.info("Detecting Jupyter client type")
        val currentHandle = ProcessHandle.current()
        val ancestors = generateSequence(currentHandle) { it.parent().orElse(null) }.toList()

        for (handle in ancestors) {
            val info = handle.info()
            val command = info.command().orElse("")
            val arguments = info.arguments().orElse(emptyArray()).toList()

            log.info("Inspecting process: $command ${arguments.joinToString(" ")}")
            val correctDetector = detectors.firstOrNull { it.isThisClient(command, arguments) } ?: continue

            log.info("Detected type is ${correctDetector.type}")
            return correctDetector.type
        }

        log.info("Client type has not been detected")
        return JupyterClientType.UNKNOWN
    }

    private interface Detector {
        val type: JupyterClientType
        fun isThisClient(command: String, arguments: List<String>): Boolean
    }

    private fun detector(type: JupyterClientType, predicate: (String, List<String>) -> Boolean) = object : Detector {
        override val type: JupyterClientType get() = type
        override fun isThisClient(command: String, arguments: List<String>): Boolean {
            return predicate(command, arguments)
        }
    }

    private val detectors = listOf<Detector>(
        detector(JupyterClientType.JUPYTER_NOTEBOOK) { command, args ->
            "jupyter-notebook" in command || args.any { "jupyter-notebook" in it }
        },
        detector(JupyterClientType.JUPYTER_LAB) { command, args ->
            "jupyter-lab" in command || args.any { "jupyter-lab" in it }
        },
        detector(JupyterClientType.KERNEL_TESTS) { command, _ ->
            command.endsWith("idea64.exe")
        },
    )
}
