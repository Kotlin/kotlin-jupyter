package org.jetbrains.kotlinx.jupyter.example

import org.jetbrains.kotlinx.jupyter.api.FieldHandlerByRuntimeClass
import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration

@JupyterLibrary
class GettingStartedIntegration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        /**
         * Import package that contains this class
         * Equivalent to `import("org.jetbrains.kotlinx.jupyter.example.*")`
         */
        importPackage<GettingStartedIntegration>()

        /**
         * If cell returns [Person] instance, it will be rendered to
         * this nice-looking string
         */
        render<Person> { person ->
            "${person.name} ${person.lastName}, ${person.age} y.o."
        }

        /**
         * If variable with type [Car] is created in cell, it is added to its owner
         * cars list
         */
        onVariable<Car> { car, kProperty ->
            val owner = car.owner
            if (car !in owner.cars) {
                owner.cars.add(car)
                println("${owner.name} now owns car `${kProperty.name}`")
            }
        }

        /**
         * Creates new variable for each person with name equivalent to the person's name
         */
        addTypeConverter(
            FieldHandlerByRuntimeClass(Person::class) { host, person, kProperty ->
                person as Person
                if (person.name != kProperty.name) host.execute("val `${person.name}` = ${kProperty.name}")
            },
        )

        /**
         * For each class marked with [MarkerAnnotation] log its creation.
         */
        onClassAnnotation<MarkerAnnotation> { classes ->
            classes.forEach {
                println("Class ${it.simpleName} was marked!")
            }
        }

        /**
         * Before each cell execution this code will be evaluated
         */
        beforeCellExecution {
            println("Before cell callback")
        }

        /**
         * After each cell execution this code will be evaluated
         * Here we may use the execution cell results
         */
        afterCellExecution { _, result ->
            println("Cell [${notebook.currentCell?.id}] was evaluated, result is $result")
        }
    }
}
