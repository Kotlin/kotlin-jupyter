package jupyter.kotlin

import org.jetbrains.kotlin.script.*
import kotlin.reflect.KClass
import kotlin.reflect.primaryConstructor

@ScriptTemplateDefinition(resolver = KotlinJupyterScriptDependenciesResolverProxy::class)
abstract class KotlinJupyterScriptTemplate

// TODO: implement scripting support that allows to get resolver indirectly using template as a key
// (to remove necessity to pull all scripting-related code into client classpath)
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
