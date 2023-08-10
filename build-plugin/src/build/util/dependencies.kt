@file:Suppress("UnstableApiUsage")

package build.util

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.JvmLibrary
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.getArtifacts
import org.gradle.language.base.artifact.SourcesArtifact
import java.io.File

fun DependencyHandlerScope.implementation(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("implementation", dependency)
fun DependencyHandlerScope.compileOnly(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("compileOnly", dependency)
fun DependencyHandlerScope.runtimeOnly(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("runtimeOnly", dependency)
fun DependencyHandlerScope.testImplementation(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("testImplementation", dependency)
fun DependencyHandlerScope.testRuntimeOnly(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("testRuntimeOnly", dependency)

fun Configuration.resolveSources(project: Project): Collection<File> {
    val componentIds = incoming.resolutionResult.allDependencies.map { it.from.id }

    @Suppress("UnstableApiUsage")
    val sourceRequestResult = project.dependencies.createArtifactResolutionQuery()
        .forComponents(componentIds)
        .withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java)
        .execute()
    val sourcesFromLibraries = sourceRequestResult.resolvedComponents.flatMap {
        it.getArtifacts(SourcesArtifact::class).mapNotNull { res ->
            (res as? ResolvedArtifactResult)?.file
        }
    }

    val projectComponentIds = componentIds.filterIsInstance<DefaultProjectComponentIdentifier>()
    val sourcesFromProjects = projectComponentIds.flatMap { componentId ->
        val currentProject = project.project(componentId.projectPath().path)
        val sourceSets = currentProject.extensions.getByType(SourceSetContainer::class.java)
        sourceSets.flatMap { it.allSource.srcDirs }
    }

    return sourcesFromLibraries + sourcesFromProjects
}
