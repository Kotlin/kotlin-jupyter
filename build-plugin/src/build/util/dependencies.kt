@file:Suppress("UnstableApiUsage")

package build.util

import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.project

fun DependencyHandlerScope.implementation(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("implementation", dependency)
fun DependencyHandlerScope.compileOnly(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("compileOnly", dependency)
fun DependencyHandlerScope.testImplementation(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("testImplementation", dependency)
fun DependencyHandlerScope.testRuntimeOnly(dependency: Provider<MinimalExternalModuleDependency>) = addProvider("testRuntimeOnly", dependency)

fun DependencyHandler.shadowOf(projectDependency: ProjectDependency) =
    project(path = projectDependency.path, configuration = "shadow")
