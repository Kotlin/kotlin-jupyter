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

    fun scheduleExecution(code: String)
}

abstract class ScriptTemplateWithDisplayHelpers(val __host: KotlinKernelHost?) {

    fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))

    fun HTML(text: String, isolated: Boolean = false) = MIME("text/html" to text).also { it.isolatedHtml = isolated }

    fun DISPLAY(value: Any) = __host!!.display(value)

    fun EXECUTE(code: String) = __host!!.scheduleExecution(code)

    val Out: List<Any?> = ReplOutputs
}

fun mimeResult(vararg mimeToData: Pair<String, String>): MimeTypedResult = MimeTypedResult(mapOf(*mimeToData))
fun textResult(text: String): MimeTypedResult = MimeTypedResult(mapOf("text/plain" to text))

class MimeTypedResult(mimeData: Map<String, String>, var isolatedHtml: Boolean = false) : Map<String, String> by mimeData

val ReplOutputs = mutableListOf<Any?>()