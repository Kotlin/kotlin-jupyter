package org.jetbrains.kotlinx.jupyter.api.dependencies

/**
 * Resolves dependencies and manages their configuration.
 * Responsible for handling repositories, resolving dependency descriptions into actual
 * classpath, and managing options for dependency resolution.
 */
interface DependencyResolver {
    var resolveSources: Boolean
    var resolveMpp: Boolean

    fun addRepositories(repositories: List<RepositoryDescription>)

    /**
     * Pure function
     */
    fun resolve(dependencyDescriptions: Collection<DependencyDescription>): ResolutionResult
}
