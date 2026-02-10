package org.jetbrains.kotlinx.jupyter.compiler

import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.dependencies.RepositoryDescription
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.dependencies.JupyterScriptDependenciesResolver
import kotlin.script.experimental.api.ResultWithDiagnostics

/**
 * Implementation of KernelCallbacks that bridges the new CompilerService API
 * with the existing kernel infrastructure.
 *
 * @param dependencyResolver The resolver for handling @DependsOn and @Repository annotations
 * @param onImportsReported Callback for when imports are reported by the compiler
 * @param onDeclarationsReported Callback for when declarations are reported by the compiler
 */
internal class KernelCallbacksImpl(
    private val dependencyResolver: JupyterScriptDependenciesResolver,
    private val onImportsReported: (List<String>) -> Unit = {},
    private val onDeclarationsReported: (List<DeclarationInfo>) -> Unit = {},
) : KernelCallbacks {
    override suspend fun reportImports(imports: List<String>) {
        onImportsReported(imports)
    }

    override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {
        onDeclarationsReported(declarations)
    }

    override suspend fun resolveDependencies(
        annotations: List<DependencyAnnotation>,
    ): DependencyResolutionResult {
        if (annotations.isEmpty()) {
            return DependencyResolutionResult.Success(emptyList())
        }

        // Separate repository and dependency annotations
        val repositories = annotations.filterIsInstance<DependencyAnnotation.Repository>()
        val dependencies = annotations.filterIsInstance<DependencyAnnotation.DependsOn>()

        // Add repositories first
        if (repositories.isNotEmpty()) {
            val repoDescriptions = repositories.map { repo ->
                RepositoryDescription(repo.url, repo.username, repo.password)
            }
            dependencyResolver.addRepositories(repoDescriptions)
        }

        // Resolve dependencies
        if (dependencies.isEmpty()) {
            return DependencyResolutionResult.Success(emptyList())
        }

        // Convert to kernel's annotation format and resolve
        val kernelAnnotations = dependencies.map { dep ->
            jupyter.kotlin.DependsOn(dep.value)
        }

        return when (val result = dependencyResolver.resolveFromAnnotations(kernelAnnotations)) {
            is ResultWithDiagnostics.Success -> {
                val classpath = result.value.map { it.absolutePath }
                DependencyResolutionResult.Success(classpath)
            }
            is ResultWithDiagnostics.Failure -> {
                val message = result.reports.joinToString("\n") { it.message }
                DependencyResolutionResult.Failure(message)
            }
        }
    }
}
