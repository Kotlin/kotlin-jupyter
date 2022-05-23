package jupyter.kotlin.providers

import org.jetbrains.kotlinx.jupyter.api.SessionOptions

interface SessionOptionsProvider {
    val sessionOptions: SessionOptions
}
