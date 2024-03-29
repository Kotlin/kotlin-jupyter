package org.jetbrains.kotlinx.jupyter.common

enum class ReplCommand(val desc: String) {
    HELP("display help"),
    CLASSPATH("show current classpath"),
    VARS("get visible variables values"),
    ;

    val nameForUser = getNameForUser(name)

    companion object : ReplEnum<ReplCommand> {
        val type =
            object : ReplEnum.Type {
                override val name = "command"
            }

        private val enumValues =
            values().associate {
                it.nameForUser to ReplEnum.CodeInsightValue(it, it.nameForUser, it.desc, type)
            }

        override val codeInsightValues by lazy {
            enumValues.values.toList()
        }

        override fun valueOfOrNull(name: String) = enumValues[name]
    }
}
