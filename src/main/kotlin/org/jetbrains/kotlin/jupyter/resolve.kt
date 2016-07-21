package org.jetbrains.kotlin.jupyter

import jupyter.kotlin.DependsOn
import jupyter.kotlin.KotlinJupyterScriptDependenciesResolverProxy
import jupyter.kotlin.KotlinJupyterScriptTemplate
import jupyter.kotlin.Repository
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ScriptDescriptor
import org.jetbrains.kotlin.jupyter.resolvers.DirectResolver
import org.jetbrains.kotlin.jupyter.resolvers.FlatLibDirectoryResolver
import org.jetbrains.kotlin.jupyter.resolvers.MavenResolver
import org.jetbrains.kotlin.jupyter.resolvers.Resolver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.script.*
import org.jetbrains.kotlin.types.KotlinType
import java.io.File

class KotlinJupyterScriptDependenciesResolver : ScriptDependenciesResolverEx {

    @AcceptedAnnotations(DependsOn::class, Repository::class)
    override fun resolve(script: ScriptContents,
                         environment: Map<String, Any?>?,
                         previousDependencies: KotlinScriptExternalDependencies?
    ): KotlinScriptExternalDependencies?
    {
        script.annotations.forEach {
            when (it) {
                is Repository ->
                    when {
                        File(it.value).run { exists() && isDirectory } -> resolvers.add( FlatLibDirectoryResolver(File(it.value)))
                        else -> throw IllegalArgumentException("Illegal argument for Repository annotation: ${it.value}")
                    }
                is DependsOn -> {}
                is InvalidScriptResolverAnnotation -> throw Exception("Invalid annotation ${it.name}", it.error)
                else -> throw Exception("Unknown annotation ${it.javaClass}")
            }
        }
        val cp = script.annotations.filterIsInstance(DependsOn::class.java).flatMap { dep ->
            resolvers.asSequence().mapNotNull { it.tryResolve(dep) }.firstOrNull() ?:
                    throw Exception("Unable to resolve dependency $dep")
        }
        return if (previousDependencies != null && cp.isEmpty()) previousDependencies
            else object : KotlinScriptExternalDependencies {
                    override val classpath: Iterable<File> = cp
                    override val imports: Iterable<String> =
                            previousDependencies?.let { emptyList<String>() } ?: listOf(DependsOn::class.java.`package`.name + ".*")
                }
    }

    companion object {
        // NOTE: this doesn't support multiple clients yet
        // TODO: add some id/uri to ScriptContent, fill it then creating a script VirtualFile and use it as a key to resolvers
        val resolvers: MutableList<Resolver> = arrayListOf(DirectResolver(), MavenResolver())
    }
}

class KotlinJupyterScriptDefinition : KotlinScriptDefinition {

    init {
        // TODO: see comments to KotlinJupyterScriptDependenciesResolverProxy
        synchronized(KotlinJupyterScriptDependenciesResolverProxy) {
            if (KotlinJupyterScriptDependenciesResolverProxy.actualResolverClass == null)
                KotlinJupyterScriptDependenciesResolverProxy.actualResolverClass = KotlinJupyterScriptDependenciesResolver::class
        }
    }

    private val def = KotlinScriptDefinitionFromTemplate(KotlinJupyterScriptTemplate::class,
                                                         KotlinJupyterScriptDependenciesResolverProxy())

    override val name: String = "Kotlin Jupyter Script"

    // TODO: make more effective for merged script file
    override fun <TF> getDependenciesFor(file: TF, project: Project, previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? =
            def.getDependenciesFor(file, project, previousDependencies)

    override fun getScriptParameters(scriptDescriptor: ScriptDescriptor): List<ScriptParameter> = emptyList()

    override fun getScriptParametersToPassToSuperclass(scriptDescriptor: ScriptDescriptor): List<Name> = emptyList()

    override fun getScriptSupertypes(scriptDescriptor: ScriptDescriptor): List<KotlinType> = def.getScriptSupertypes(scriptDescriptor)

    override fun <TF> isScript(file: TF): Boolean = StandardScriptDefinition.isScript(file)
}