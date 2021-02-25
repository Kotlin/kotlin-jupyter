package jupyter.kotlin

sealed class Modificator {
    data class AddColumn<T, R>(val name: String, val getValue: T.() -> R) : Modificator()
    data class RemoveColumn(val name: String) : Modificator()
}

data class ModifiedList<T>(val source: List<T>, val modificator: Modificator) : List<T> by source

fun <T, R> List<T>.addColumn(name: String, value: T.() -> R) = ModifiedList(this, Modificator.AddColumn(name, value))

fun <T> List<T>.removeColumn(name: String) = ModifiedList(this, Modificator.RemoveColumn(name))
