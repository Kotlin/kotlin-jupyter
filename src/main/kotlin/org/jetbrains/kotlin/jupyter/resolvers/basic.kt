package org.jetbrains.kotlin.jupyter.resolvers

import jupyter.kotlin.DependsOn
import java.io.File

interface Resolver {
    fun tryResolve(dependsOn: DependsOn): Iterable<File>?
}

class DirectResolver : Resolver {
    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? =
            if (dependsOn.value.isNotBlank() && File(dependsOn.value).exists()) listOf(File(dependsOn.value)) else null
}

class FlatLibDirectoryResolver(val path: File) : Resolver {

    init {
        if (!path.exists() || !path.isDirectory) throw IllegalArgumentException("Invalid flat lib directory repository path '$path'")
    }

    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? =
            when {
                dependsOn.value.isNotBlank() && File(path, dependsOn.value).exists() -> listOf(File(path, dependsOn.value))
                // TODO: add coordinates and wildcard matching
                else -> null
            }
}
