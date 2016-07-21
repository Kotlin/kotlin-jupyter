@file:Suppress("unused")

package jupyter.kotlin

class Result(mimeData: Map<String, Any>) : Map<String, Any> by mimeData

fun resultOf(vararg mimeToData: Pair<String, Any>): Result = Result(mapOf(*mimeToData))

fun textResult(text: String): Result = Result(mapOf("text/plain" to text))
