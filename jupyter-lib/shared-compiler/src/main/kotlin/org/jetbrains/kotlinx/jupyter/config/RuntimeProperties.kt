package org.jetbrains.kotlinx.jupyter.config

import jupyter.kotlin.JavaRuntime
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import org.jetbrains.kotlinx.jupyter.compiler.util.MAX_SUPPORTED_JVM_TARGET
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelConfig
import org.jetbrains.kotlinx.jupyter.repl.ReplRuntimeProperties
import org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParams
import org.jetbrains.kotlinx.jupyter.streams.KernelStreams

fun String.parseIniConfig() =
    lineSequence()
        .map { it.split('=') }
        .filter { it.count() == 2 }
        .map { it[0] to it[1] }
        .toMap()

fun readResourceAsIniFile(
    fileName: String,
    classLoader: ClassLoader,
) = classLoader
    .getResource(fileName)
    ?.readText()
    ?.parseIniConfig()
    .orEmpty()

val kernelClassLoader: ClassLoader = KernelStreams::class.java.classLoader

val defaultRuntimeProperties by lazy {
    RuntimeKernelProperties(
        readResourceAsIniFile("kotlin-jupyter-compiler.properties", kernelClassLoader),
    )
}

fun propertyMissingError(propertyDescription: String): Nothing {
    @Suppress("UNREACHABLE_CODE")
    return error("Compiler artifact should contain $propertyDescription")
}

val currentKernelVersion by lazy {
    defaultRuntimeProperties.version ?: propertyMissingError("kernel version")
}

val currentKotlinVersion by lazy {
    defaultRuntimeProperties.kotlinVersion
}

fun createRuntimeProperties(
    kernelConfig: KernelConfig<KotlinKernelOwnParams>,
    defaultProperties: ReplRuntimeProperties = defaultRuntimeProperties,
): ReplRuntimeProperties =
    object : ReplRuntimeProperties by defaultProperties {
        override val jvmTargetForSnippets: String
            get() = kernelConfig.ownParams.jvmTargetForSnippets ?: defaultProperties.jvmTargetForSnippets
    }

class RuntimeKernelProperties(
    val map: Map<String, String>,
) : ReplRuntimeProperties {
    override val version: KotlinKernelVersion? by lazy {
        map["version"]?.let { KotlinKernelVersion.from(it) }
    }

    @Deprecated("This parameter is meaningless, do not use")
    override val librariesFormatVersion: Int
        get() = throw RuntimeException("Libraries format version is not specified!")
    override val currentBranch: String
        get() = map["currentBranch"] ?: throw RuntimeException("Current branch is not specified!")
    override val currentSha: String
        get() = map["currentSha"] ?: throw RuntimeException("Current commit SHA is not specified!")
    override val jvmTargetForSnippets by lazy {
        map["jvmTargetForSnippets"]
            ?: listOf(
                JavaRuntime.javaVersion,
                MAX_SUPPORTED_JVM_TARGET,
            ).minBy { it.versionInteger }.versionString
    }
    override val kotlinVersion: String
        get() = map["kotlinVersion"] ?: throw RuntimeException("Kotlin version is not specified!")
}
