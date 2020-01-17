package jupyter.kotlin.completion

import kotlin.reflect.KFunction

import jupyter.kotlin.completion.KotlinReflectUtil.functionSignature
import jupyter.kotlin.completion.KotlinReflectUtil.shorten


class KotlinFunctionInfo(val function: KFunction<*>, val line: Any) : Comparable<KotlinFunctionInfo> {

    val name: String
        get() = function.name

    fun toString(shortenTypes: Boolean): String {
        return if (shortenTypes) {
            shorten(toString())
        } else toString()
    }

    override fun toString(): String {
        return functionSignature(function)
    }

    override fun compareTo(other: KotlinFunctionInfo): Int {
        return this.toString().compareTo(other.toString())
    }

    override fun hashCode(): Int {
        return this.toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is KotlinFunctionInfo) {
            this.toString() == other.toString()
        } else false
    }
}
