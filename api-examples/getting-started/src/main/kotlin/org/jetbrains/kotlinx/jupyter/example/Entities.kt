package org.jetbrains.kotlinx.jupyter.example

import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.Renderable

data class Person(
    val name: String,
    val lastName: String,
    val age: Int,
    val cars: MutableList<Car> = mutableListOf(),
)

data class Car(
    val model: String,
    val inceptionYear: Int,
    val owner: Person,
)

class MyClass : Renderable {
    private fun toHTML(): String = "<p>Instance of MyClass</p>"

    override fun render(notebook: Notebook) = HTML(toHTML())
}

annotation class MarkerAnnotation
