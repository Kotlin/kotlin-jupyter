package org.jetbrains.kotlinx.jupyter.startup.parameters

import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import java.io.File

class KernelOwnParamsBuilder(
    var scriptClasspath: List<File>? = null,
    var homeDir: File? = null,
    var debugPort: Int? = null,
    var clientType: String? = null,
    var jvmTargetForSnippets: String? = null,
    var replCompilerMode: ReplCompilerMode? = null,
    var extraCompilerArguments: List<String>? = null,
) {
    constructor(kernelOwnParams: KernelOwnParams) : this(
        scriptClasspath = kernelOwnParams.scriptClasspath,
        homeDir = kernelOwnParams.homeDir,
        debugPort = kernelOwnParams.debugPort,
        clientType = kernelOwnParams.clientType,
        jvmTargetForSnippets = kernelOwnParams.jvmTargetForSnippets,
        replCompilerMode = kernelOwnParams.replCompilerMode,
        extraCompilerArguments = kernelOwnParams.extraCompilerArguments,
    )

    val boundParameters =
        listOf(
            MutableBoundKernelParameter(scriptClasspathParameter, ::scriptClasspath),
            MutableBoundKernelParameter(homeDirParameter, ::homeDir),
            MutableBoundKernelParameter(debugPortParameter, ::debugPort),
            MutableBoundKernelParameter(clientTypeParameter, ::clientType),
            MutableBoundKernelParameter(jvmTargetParameter, ::jvmTargetForSnippets),
            MutableBoundKernelParameter(replCompilerModeParameter, ::replCompilerMode),
            MutableBoundKernelParameter(extraCompilerArgumentsParameter, ::extraCompilerArguments),
        )

    fun build(): KernelOwnParams =
        KernelOwnParams(
            scriptClasspath = scriptClasspath ?: emptyList(),
            homeDir = homeDir,
            debugPort = debugPort,
            clientType = clientType,
            jvmTargetForSnippets = jvmTargetForSnippets,
            replCompilerMode = replCompilerMode ?: ReplCompilerMode.DEFAULT,
            extraCompilerArguments = extraCompilerArguments ?: emptyList(),
        )
}
