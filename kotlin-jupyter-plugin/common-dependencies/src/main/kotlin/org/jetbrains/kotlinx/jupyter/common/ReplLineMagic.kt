package org.jetbrains.kotlinx.jupyter.common

enum class ReplLineMagic(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    USE("injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers", "klaxon(5.5), lets-plot"),
    TRACK_CLASSPATH("logs any changes of current classpath. Useful for debugging artifact resolution failures"),
    TRACK_EXECUTION("logs pieces of code that are going to be executed. Useful for debugging of libraries support"),
    DUMP_CLASSES_FOR_SPARK("stores compiled repl classes in special folder for Spark integration", visibleInHelp = false),
    USE_LATEST_DESCRIPTORS("use latest versions of library descriptors available. By default, bundled descriptors are used", "-[on|off]"),
    OUTPUT("output capturing settings", "--max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400"),
    LOG_LEVEL("set logging level", "[off|error|warn|info|debug]"),
    LOG_HANDLER("manage logging handlers", "[list | remove <name> | add <name> --<type> [... typeArgs]]", visibleInHelp = false);

    val nameForUser = getNameForUser(name)

    companion object : ReplEnum<ReplLineMagic> {
        val type = object : ReplEnum.Type {
            override val name = "magic"
        }

        private val enumValues = values().associate {
            it.nameForUser to ReplEnum.CodeInsightValue(it, it.nameForUser, it.desc, type)
        }

        override val codeInsightValues by lazy {
            enumValues.values.toList()
        }

        override fun valueOfOrNull(name: String) = enumValues[name]
    }
}
