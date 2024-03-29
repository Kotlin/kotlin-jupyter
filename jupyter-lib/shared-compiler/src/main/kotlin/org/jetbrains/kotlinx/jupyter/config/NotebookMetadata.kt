package org.jetbrains.kotlinx.jupyter.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LanguageInfo(
    val name: String,
    val version: String,
    val mimetype: String,
    @SerialName("file_extension")
    val fileExtension: String,
    @SerialName("pygments_lexer")
    val pygmentsLexer: String,
    @SerialName("codemirror_mode")
    val codemirrorMode: String,
    @SerialName("nbconvert_exporter")
    val nbConvertExporter: String,
)

val notebookLanguageInfo: LanguageInfo by lazy {
    LanguageInfo(
        "kotlin",
        currentKotlinVersion,
        "text/x-kotlin",
        ".kt",
        "kotlin",
        "text/x-kotlin",
        "",
    )
}

@Serializable
class KotlinKernelSpec(
    @SerialName("display_name")
    val displayName: String,
    val language: String,
    val name: String,
)

val notebookKernelSpec: KotlinKernelSpec by lazy {
    KotlinKernelSpec(
        "Kotlin",
        "kotlin",
        "kotlin",
    )
}
