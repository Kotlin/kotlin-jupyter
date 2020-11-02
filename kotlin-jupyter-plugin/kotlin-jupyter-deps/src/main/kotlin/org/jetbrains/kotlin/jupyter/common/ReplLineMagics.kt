package org.jetbrains.kotlin.jupyter.common

enum class ReplLineMagics(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    use("injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers", "klaxon(5.0.1), lets-plot"),
    trackClasspath("logs any changes of current classpath. Useful for debugging artifact resolution failures"),
    trackExecution("logs pieces of code that are going to be executed. Useful for debugging of libraries support"),
    dumpClassesForSpark("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false),
    useLatestDescriptors("use latest versions of library descriptors available. By default, bundled descriptors are used", "-[on|off]"),
    output("output capturing settings", "--max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400");

    companion object {
        fun valueOfOrNull(name: String): ReplLineMagics? {
            return try {
                valueOf(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
