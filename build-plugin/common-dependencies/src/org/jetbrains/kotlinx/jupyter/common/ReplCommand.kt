package org.jetbrains.kotlinx.jupyter.common

enum class ReplCommand(
    val desc: String,
) {
    HELP("Displays help information with details of the notebook version, line magics, and supported libraries."),
    CLASSPATH(
        "Displays the current classpath of your notebook environment, " +
            "showing a list of locations where the notebook searches for libraries and resources.",
    ),
    VARS("Displays information about the declared variables and their values."),
    ;

    val nameForUser = getNameForUser(name)

    companion object : ReplEnum<ReplCommand> {
        val type =
            object : ReplEnum.Type {
                override val name = "command"
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
