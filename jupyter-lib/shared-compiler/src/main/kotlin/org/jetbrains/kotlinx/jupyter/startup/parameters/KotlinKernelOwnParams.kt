package org.jetbrains.kotlinx.jupyter.startup.parameters

import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelOwnParams
import org.jetbrains.kotlinx.jupyter.protocol.startup.parameters.KernelOwnParamsBuilder
import java.io.File

/**
 * To add a new kernel argument:
 * 1. Add it to this constructor
 * 2. Define how it's serialized in `KernelCmdParameters.kt`
 * 3. Add it to both constructors of [org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParamsBuilder]
 * 4. Add one more bound parameter in [org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParamsBuilder]
 * 5. Add it to [org.jetbrains.kotlinx.jupyter.startup.parameters.KotlinKernelOwnParamsBuilder.build]
 */
class KotlinKernelOwnParams(
    val scriptClasspath: List<File> = emptyList(),
    val homeDir: File?,
    val debugPort: Int? = null,
    val clientType: String? = null,
    val jvmTargetForSnippets: String? = null,
    val replCompilerMode: ReplCompilerMode = ReplCompilerMode.DEFAULT,
    val extraCompilerArguments: List<String> = emptyList(),
) : KernelOwnParams {
    override fun createBuilder(): KernelOwnParamsBuilder<KotlinKernelOwnParams> = KotlinKernelOwnParamsBuilder(this)
}
