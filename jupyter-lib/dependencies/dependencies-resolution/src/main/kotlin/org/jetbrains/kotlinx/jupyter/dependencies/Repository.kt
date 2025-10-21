package org.jetbrains.kotlinx.jupyter.dependencies

/**
 * Describes a dependency repository.
 *
 * The [value] may be a Maven-like URL (including file://) or a filesystem path, depending on the resolver.
 * Optional [username] and [password] can be provided for authenticated repositories. Environment variable
 * indirection is supported by resolvers that call tryResolveEnvironmentVariable.
 *
 * @property value Repository location as a string.
 * @property username Optional username; empty string if not provided.
 * @property password Optional password; empty string if not provided.
 */
class Repository(
    val value: String,
    val username: String = "",
    val password: String = "",
)
