package org.jetbrains.kotlinx.jupyter.api.graphs.labels

import kotlin.reflect.KClass

/**
 * Label representing [kClass] with all members in HTML table
 */
class KClassLabel(
    private val kClass: KClass<*>,
) : RecordTableLabel() {
    override val mainText get() = kClass.simpleName.toString()

    override val properties: Collection<Iterable<String>>
        get() = kClass.members.map { listOf(it.name, it.returnType.toString()) }

    override val attributes = TableAttributes.build { cellspacing = 0 }
}
