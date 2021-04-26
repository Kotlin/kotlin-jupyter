package org.jetbrains.kotlinx.jupyter.common

enum class ReplCommand(val desc: String) {
    HELP("display help"),
    CLASSPATH("show current classpath");

    val nameForUser = getNameForUser(name)

    companion object : ReplEnum<ReplCommand> {
        private val names = values().associateBy { it.nameForUser }

        val type = object : ReplEnum.Type {
            override val name = "command"
        }

        override val codeInsightValues by lazy {
            names.map { (userName, value) -> ReplEnum.CodeInsightValue(value, userName, value.desc, type) }
        }

        override fun valueOfOrNull(name: String) = names[name]
    }
}
