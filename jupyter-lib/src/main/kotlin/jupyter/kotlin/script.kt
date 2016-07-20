package jupyter.kotlin

import org.jetbrains.kotlin.script.AcceptedAnnotations
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents
import org.jetbrains.kotlin.script.ScriptDependenciesResolverEx
import kotlin.reflect.KClass
import kotlin.reflect.primaryConstructor

abstract class KotlinJupyterScriptTemplate

class KotlinJupyterScriptDependenciesResolverProxy : ScriptDependenciesResolverEx {

    val resolver = actualResolverClass!!.primaryConstructor!!.call()

    @AcceptedAnnotations(DependsOn::class, Repository::class)
    override fun resolve(script: ScriptContents,
                         environment: Map<String, Any?>?,
                         previousDependencies: KotlinScriptExternalDependencies?
    ): KotlinScriptExternalDependencies? =
    resolver.resolve(script, environment, previousDependencies)

    companion object {
        var actualResolverClass: KClass<out ScriptDependenciesResolverEx>? = null
    }
}
