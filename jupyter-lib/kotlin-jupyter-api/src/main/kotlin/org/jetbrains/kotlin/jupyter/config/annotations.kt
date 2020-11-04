package org.jetbrains.kotlin.jupyter.config

import org.jetbrains.kotlin.jupyter.api.CodeExecution
import org.jetbrains.kotlin.jupyter.api.Notebook
import org.jetbrains.kotlin.jupyter.api.ResultsAccessor

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
abstract class ScriptTemplateWithDisplayHelpers(
    val notebook: Notebook<*>,
) {
    fun DISPLAY(value: Any) = notebook.host.display(value)

    fun EXECUTE(code: String) = notebook.host.scheduleExecution(CodeExecution(code))

    val Out: ResultsAccessor get() = notebook.results

    val JavaRuntimeUtils get() = notebook.runtimeUtils
}
