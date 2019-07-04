@file:Suppress("unused")

package jupyter.kotlin

import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(fileExtension = "jupyterscript.kts")
abstract class ScriptTemplateWithDisplayHelpers {
    fun resultOf(vararg mimeToData: Pair<String, Any>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
}

fun mimeResult(vararg mimeToData: Pair<String, Any>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): Map<String, Any> = MimeTypedResult(mapOf("text/plain" to text))

class MimeTypedResult(mimeData: Map<String, Any>): Map<String, Any> by mimeData


