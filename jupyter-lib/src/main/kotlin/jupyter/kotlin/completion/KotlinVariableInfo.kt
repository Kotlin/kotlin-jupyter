package jupyter.kotlin.completion

import jupyter.kotlin.completion.KotlinReflectUtil.shorten
import kotlin.reflect.KProperty

class KotlinVariableInfo(private val value: Any?, private val descriptor: KProperty<*>) {

    val name: String
        get() = descriptor.name

    val type: String
        get() = descriptor.returnType.toString()

    fun toString(shortenTypes: Boolean): String {
        var type: String = type
        if (shortenTypes) {
            type = shorten(type)
        }
        return "$name: $type = $value"
    }

    override fun toString(): String {
        return toString(false)
    }
}
