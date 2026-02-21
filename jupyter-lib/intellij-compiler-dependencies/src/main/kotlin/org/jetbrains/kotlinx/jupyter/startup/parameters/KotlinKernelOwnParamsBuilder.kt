package org.jetbrains.kotlinx.jupyter.startup.parameters

import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelOwnParamsBuilder
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.MutableBoundKernelParameter
import java.io.File

class KotlinKernelOwnParamsBuilder(
    var scriptClasspath: List<File>? = null,
    var homeDir: File? = null,
    var debugPort: Int? = null,
    var clientType: String? = null,
    var jvmTargetForSnippets: String? = null,
    var replCompilerMode: ReplCompilerMode? = null,
    var extraCompilerArguments: List<String>? = null,
) : KernelOwnParamsBuilder<KotlinKernelOwnParams> {
    constructor(kernelOwnParams: KotlinKernelOwnParams) : this(
        scriptClasspath = kernelOwnParams.scriptClasspath,
        homeDir = kernelOwnParams.homeDir,
        debugPort = kernelOwnParams.debugPort,
        clientType = kernelOwnParams.clientType,
        jvmTargetForSnippets = kernelOwnParams.jvmTargetForSnippets,
        replCompilerMode = kernelOwnParams.replCompilerMode,
        extraCompilerArguments = kernelOwnParams.extraCompilerArguments,
    )

    override val boundParameters =
        listOf(
            MutableBoundKernelParameter(scriptClasspathParameter, ::scriptClasspath),
            MutableBoundKernelParameter(homeDirParameter, ::homeDir),
            MutableBoundKernelParameter(debugPortParameter, ::debugPort),
            MutableBoundKernelParameter(clientTypeParameter, ::clientType),
            MutableBoundKernelParameter(jvmTargetParameter, ::jvmTargetForSnippets),
            MutableBoundKernelParameter(replCompilerModeParameter, ::replCompilerMode),
            MutableBoundKernelParameter(extraCompilerArgumentsParameter, ::extraCompilerArguments),
        )

    override fun build(): KotlinKernelOwnParams =
        KotlinKernelOwnParams(
            scriptClasspath = scriptClasspath ?: emptyList(),
            homeDir = homeDir,
            debugPort = debugPort,
            clientType = clientType,
            jvmTargetForSnippets = jvmTargetForSnippets,
            replCompilerMode = replCompilerMode ?: ReplCompilerMode.DEFAULT,
            extraCompilerArguments = extraCompilerArguments ?: emptyList(),
        )
}
