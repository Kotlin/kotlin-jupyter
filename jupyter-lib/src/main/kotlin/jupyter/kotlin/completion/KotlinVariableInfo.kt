package jupyter.kotlin.completion

import jupyter.kotlin.completion.KotlinReflectUtil.shorten
import kotlin.reflect.KProperty

class KotlinVariableInfo(val value: Any?, val descriptor: KProperty<*>, val line: Any) {

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
