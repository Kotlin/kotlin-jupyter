package org.jetbrains.kotlinx.jupyter.common

/**
 * This class defines the magic commands available to users of a notebook. They are accessed
 * by using the `%<enumNameInCamelCase>` syntax inside a notebook.
 *
 * Example:
 *
 * ```
 * // Snippet 1
 * %useLatestDescriptors
 * println("Hello world")
 * ```
 *
 * Adding a new magic value also requires registering a handler in a magic handler class that extends
 * `org.jetbrains.kotlinx.jupyter.magics.BaseMagicsHandler` and adding it to the `org.jetbrains.kotlinx.jupyter.magics.MagicsHandlerRegistry`.
 */
enum class ReplLineMagic(
    val desc: String,
    val argumentsUsage: String? = null,
    val visibleInHelp: Boolean = true,
) {
    USE(
        "Imports supported libraries and injects code from these libraries" +
            "(artifact resolution, default imports, initialization code, and type renderers).",
        "klaxon(5.5), lets-plot",
    ),
    TRACK_CLASSPATH(
        "Logs any changes of the current classpath. This command is useful for debugging artifact resolution failures.",
        "[on/off]",
    ),
    TRACK_EXECUTION("Logs pieces of code to be executed. This command is useful for debugging libraries support.", "[all/generated/off]"),
    DUMP_CLASSES_FOR_SPARK(
        "Stores compiled REPL classes in a special folder for integrating with Spark.",
        "[on/off]",
        visibleInHelp = false,
    ),
    USE_LATEST_DESCRIPTORS(
        "Sets the latest versions of available library descriptors instead of bundled descriptors (used by default). " +
            "Note that bundled descriptors are preferred because the current kernel version might not support the latest descriptors. " +
            "For better notebook stability, use bundled descriptors.",
        "[on/off]",
    ),
    OUTPUT("Configures the output capturing settings.", "--max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400"),
    LOG_LEVEL("Sets logging level.", "[off/error/warn/info/debug]"),
    LOG_HANDLER("Manages logging handlers.", "[list / remove <name> / add <name> --<type> [... typeArgs]]", visibleInHelp = false),
    ;

    val nameForUser = getNameForUser(name)

    companion object : ReplEnum<ReplLineMagic> {
        val type =
            object : ReplEnum.Type {
                override val name = "magic"
            }

        private val enumValues =
            entries.associate {
                it.nameForUser to ReplEnum.CodeInsightValue(it, it.nameForUser, it.desc, type)
            }

        override val codeInsightValues by lazy {
            enumValues.values.toList()
        }

        override fun valueOfOrNull(name: String) = enumValues[name]
    }
}
