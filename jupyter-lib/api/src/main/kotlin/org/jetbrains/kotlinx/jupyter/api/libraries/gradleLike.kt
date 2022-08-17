package org.jetbrains.kotlinx.jupyter.api.libraries

import java.io.File

interface RepositoryHandlerScope {
    fun maven(url: String)
    fun dir(dir: File)
}

interface DependencyHandlerScope {
    fun implementation(coordinates: String)
    fun implementation(group: String, artifact: String, version: String)
}

fun RepositoryHandlerScope.mavenCentral() = maven("https://repo.maven.apache.org/maven2/")
fun RepositoryHandlerScope.google() = maven("https://dl.google.com/dl/android/maven2/")
fun RepositoryHandlerScope.mavenLocal() = maven("*mavenLocal")

fun JupyterIntegration.Builder.repositories(action: RepositoryHandlerScope.() -> Unit) {
    RepositoryHandlerScopeImpl(this).action()
}

fun JupyterIntegration.Builder.dependencies(action: DependencyHandlerScope.() -> Unit) {
    DependencyHandlerScopeImpl(this).action()
}

private class RepositoryHandlerScopeImpl(private val builder: JupyterIntegration.Builder) : RepositoryHandlerScope {
    override fun dir(dir: File) {
        builder.repositories(dir.absolutePath)
    }

    override fun maven(url: String) {
        builder.repositories(url)
    }
}

private class DependencyHandlerScopeImpl(private val builder: JupyterIntegration.Builder) : DependencyHandlerScope {
    override fun implementation(coordinates: String) {
        builder.dependencies(coordinates)
    }

    override fun implementation(group: String, artifact: String, version: String) {
        implementation("$group:$artifact:$version")
    }
}
