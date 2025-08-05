package org.jetbrains.kotlinx.jupyter.api.libraries

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

class CodeExecutionCallback(
    val code: Code,
) : (KotlinKernelHost) -> Any? {
    override fun invoke(host: KotlinKernelHost): Any? = host.execute(code).value
}
