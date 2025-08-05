package org.jetbrains.kotlinx.jupyter.ext

import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.htmlResult
import java.io.File

data class HTMLAttr(
    val name: String,
    val value: String,
)

@Suppress("FunctionName")
fun HTML(
    file: File,
    isolated: Boolean = false,
): MimeTypedResult {
    val text = file.readText()
    return htmlResult(text, isolated)
}
