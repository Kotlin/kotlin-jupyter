package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

interface KotlinKernelHostProvider {
    val host: KotlinKernelHost?
}
