package org.jetbrains.kotlinx.jupyter.dependencies

import jupyter.kotlin.DependsOn

enum class DependencyAssumption {
    NO,
    MAYBE,
    YES,
}

data class Dependency(
    val value: String,
    val hasSources: DependencyAssumption = DependencyAssumption.MAYBE,
    val isMultiplatform: DependencyAssumption = DependencyAssumption.MAYBE,
)

fun Dependency.shouldResolveSources(default: Boolean): Boolean =
    when (hasSources) {
        DependencyAssumption.NO -> false
        DependencyAssumption.YES, DependencyAssumption.MAYBE -> default
    }

fun Iterable<Dependency>.shouldResolveSources(default: Boolean) = any { it.shouldResolveSources(default) }

fun Dependency.shouldResolveAsMultiplatform(default: Boolean): Boolean =
    when (isMultiplatform) {
        DependencyAssumption.NO -> false
        DependencyAssumption.MAYBE -> default
        DependencyAssumption.YES -> true
    }

fun Iterable<Dependency>.shouldResolveAsMultiplatform(default: Boolean) = any { it.shouldResolveAsMultiplatform(default) }

/**
 * This is a temporary workaround: we consider these dependencies JVM-only
 * and don't try to resolve module files for them that is very expensive with the current
 * implementation of maven resolver
 */
private fun isDefinitelyJvmOnly(artifact: String): Boolean =
    when {
        artifact.startsWith("org.jetbrains.kotlinx:dataframe") -> true
        artifact.startsWith("org.jetbrains.kotlinx:kandy") -> true
        artifact.startsWith("org.jetbrains.kotlinx:kotlin-statistics-jvm") -> true
        else -> false
    }

fun String.toDependency(): Dependency {
    val value = this
    return Dependency(
        value,
        isMultiplatform = if (isDefinitelyJvmOnly(value)) DependencyAssumption.NO else DependencyAssumption.MAYBE,
    )
}

fun DependsOn.toDependency() = value.toDependency()
