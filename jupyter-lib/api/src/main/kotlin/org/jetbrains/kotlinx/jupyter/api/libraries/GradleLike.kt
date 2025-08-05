package org.jetbrains.kotlinx.jupyter.api.libraries

import java.io.File

interface RepositoryHandlerScope {
    fun maven(url: String)

    fun maven(action: MavenRepositoryConfigurationScope.() -> Unit)

    fun dir(dir: File)
}

interface MavenRepositoryConfigurationScope {
    var url: String

    fun credentials(action: CredentialsConfigurationScope.() -> Unit)
}

interface CredentialsConfigurationScope {
    var username: String?
    var password: String?
}

interface DependencyHandlerScope {
    fun implementation(coordinates: String)

    fun implementation(
        group: String,
        artifact: String,
        version: String,
    )
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

private class RepositoryHandlerScopeImpl(
    private val builder: JupyterIntegration.Builder,
) : RepositoryHandlerScope {
    override fun dir(dir: File) {
        builder.repositories(dir.absolutePath)
    }

    override fun maven(url: String) {
        builder.repositories(url)
    }

    override fun maven(action: MavenRepositoryConfigurationScope.() -> Unit) {
        val repo = MavenRepositoryConfigurationScopeImpl().apply(action).build()
        builder.addRepository(repo)
    }
}

private class MavenRepositoryBuilder {
    var url: String? = null
    var username: String? = null
    var password: String? = null

    fun build(): KernelRepository =
        KernelRepository(
            url ?: error("URL isn't set for Maven repository"),
            username,
            password,
        )
}

private class MavenRepositoryConfigurationScopeImpl : MavenRepositoryConfigurationScope {
    val builder = MavenRepositoryBuilder()

    override var url: String
        get() = builder.url!!
        set(value) {
            builder.url = value
        }

    override fun credentials(action: CredentialsConfigurationScope.() -> Unit) {
        CredentialsConfigurationScopeImpl().action()
    }

    fun build(): KernelRepository = builder.build()

    inner class CredentialsConfigurationScopeImpl : CredentialsConfigurationScope {
        override var password: String?
            get() = builder.password
            set(value) {
                builder.password = value
            }

        override var username: String?
            get() = builder.username
            set(value) {
                builder.username = value
            }
    }
}

private class DependencyHandlerScopeImpl(
    private val builder: JupyterIntegration.Builder,
) : DependencyHandlerScope {
    override fun implementation(coordinates: String) {
        builder.dependencies(coordinates)
    }

    override fun implementation(
        group: String,
        artifact: String,
        version: String,
    ) {
        implementation("$group:$artifact:$version")
    }
}
