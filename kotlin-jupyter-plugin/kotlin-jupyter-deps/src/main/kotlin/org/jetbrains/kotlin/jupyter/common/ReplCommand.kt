package org.jetbrains.kotlin.jupyter.common

enum class ReplCommand(val desc: String) {
    HELP("display help"),
    CLASSPATH("show current classpath");

    val nameForUser = getNameForUser(name)

    companion object {
        private val names = values().map { it.nameForUser to it }.toMap()

        fun valueOfOrNull(name: String): ReplCommand? = names[name]
    }
}
