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
)

fun Dependency.shouldResolveSources(default: Boolean): Boolean =
    when (hasSources) {
        DependencyAssumption.NO -> false
        DependencyAssumption.YES, DependencyAssumption.MAYBE -> default
    }

fun Iterable<Dependency>.shouldResolveSources(default: Boolean) = any { it.shouldResolveSources(default) }

fun String.toDependency() = Dependency(this)

fun DependsOn.toDependency() = value.toDependency()
