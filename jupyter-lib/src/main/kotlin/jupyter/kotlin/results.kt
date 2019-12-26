@file:Suppress("unused")
package jupyter.kotlin

// in case of flat or direct resolvers the value should be a direct path or file name of a jar respectively
// in case of maven resolver the maven coordinates string is accepted (resolved with com.jcabi.aether library)
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class DependsOn(val value: String = "")

// only flat directory repositories are supported now, so value should be a path to a directory with jars
@Target(AnnotationTarget.FILE)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
annotation class Repository(val value: String = "")

interface KotlinKernelHost {
    fun display(value: Any)
}

abstract class ScriptTemplateWithDisplayHelpers(val __host: KotlinKernelHost?) {

    fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))

    fun HTML(text: String) = MIME("text/html" to text)

    fun DISPLAY(value: Any) = __host!!.display(value)

    val Out: List<Any?> = ReplOutputs
}

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): MimeTypedResult = MimeTypedResult(mapOf("text/plain" to text))

class MimeTypedResult(mimeData: Map<String, String>): Map<String, String> by mimeData

class DisplayResult(val value: Any)

val ReplOutputs = mutableListOf<Any?>()