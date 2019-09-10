@file:Suppress("unused")
package jupyter.kotlin

import kotlin.script.experimental.annotations.KotlinScript

// in case of flat or direct resolvers the value should be a direct path or file name of a jar respectively
// in case of maven resolver the maven coordinates string is accepted (resolved with com.jcabi.aether library)
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class DependsOn(val value: String = "", val groupId: String = "", val artifactId: String = "", val version: String = "")

// only flat directory repositories are supported now, so value should be a path to a directory with jars
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Repository(val value: String = "", val id: String = "", val url: String = "")

@KotlinScript(fileExtension = "jupyterscript.kts")
abstract class ScriptTemplateWithDisplayHelpers {
    fun resultOf(vararg mimeToData: Pair<String, Any>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
}

fun mimeResult(vararg mimeToData: Pair<String, Any>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): Map<String, Any> = MimeTypedResult(mapOf("text/plain" to text))

class MimeTypedResult(mimeData: Map<String, Any>): Map<String, Any> by mimeData