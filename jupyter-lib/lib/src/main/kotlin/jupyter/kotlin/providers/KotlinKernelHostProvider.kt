package jupyter.kotlin.providers

import org.jetbrains.kotlinx.jupyter.api.KotlinKernelHost

interface KotlinKernelHostProvider {
    val host: KotlinKernelHost?
}
