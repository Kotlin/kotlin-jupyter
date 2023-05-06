package org.jetbrains.kotlinx.jupyter.common

enum class ReplLineMagic(val desc: String, val argumentsUsage: String? = null, val visibleInHelp: Boolean = true) {
    USE("injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers", "klaxon(5.5), lets-plot"),
    TRACK_CLASSPATH("logs any changes of current classpath. Useful for debugging artifact resolution failures", "[on|off]"),
    TRACK_EXECUTION("logs pieces of code that are going to be executed. Useful for debugging of libraries support", "[all|generated|off]"),
    DUMP_CLASSES_FOR_SPARK("stores compiled repl classes in special folder for Spark integration", "[on|off]", visibleInHelp = false),
    USE_LATEST_DESCRIPTORS(
        "use latest versions of library descriptors available. By default, bundled descriptors are used. " +
            "Note that default behavior is preferred: latest descriptors versions might be not supported by current version of kernel. " +
            "So if you care about stability of the notebook, avoid using this line magic",
        "[on|off]",
    ),
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
