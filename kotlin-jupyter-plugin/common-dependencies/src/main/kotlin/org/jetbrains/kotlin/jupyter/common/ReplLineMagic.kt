package org.jetbrains.kotlin.jupyter.common

enum class ReplLineMagic(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    USE("injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers", "klaxon(5.0.1), lets-plot"),
    TRACK_CLASSPATH("logs any changes of current classpath. Useful for debugging artifact resolution failures"),
    TRACK_EXECUTION("logs pieces of code that are going to be executed. Useful for debugging of libraries support"),
    DUMP_CLASSES_FOR_SPARK("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false),
    USE_LATEST_DESCRIPTORS("use latest versions of library descriptors available. By default, bundled descriptors are used", "-[on|off]"),
    OUTPUT("output capturing settings", "--max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400");

    val nameForUser = getNameForUser(name)

    companion object {
        private val names = values().map { it.nameForUser to it }.toMap()

        fun valueOfOrNull(name: String): ReplLineMagic? = names[name]
    }
}
