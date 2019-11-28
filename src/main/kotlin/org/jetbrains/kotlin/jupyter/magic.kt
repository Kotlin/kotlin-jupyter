package org.jetbrains.kotlin.jupyter

enum class ReplLineMagics(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    use("include supported libraries", "klaxon(5.0.1), lets-plot"),
    trackClasspath("log current classpath changes"),
    trackCode("log executed code", visibleInHelp = false),
}