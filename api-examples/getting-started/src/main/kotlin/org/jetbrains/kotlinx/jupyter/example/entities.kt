package org.jetbrains.kotlinx.jupyter.example

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

annotation class MarkerAnnotation
