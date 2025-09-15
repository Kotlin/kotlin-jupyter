package org.jetbrains.kotlinx.jupyter.api.dependencies

/**
 * Represents a repository configuration for dependency resolution.
 *
 * This class is used to specify the repository details where dependencies can be downloaded from.
 * It includes the repository URL or identifier alongside optional credentials for authentication.
 *
 * @property value The URL or identifier of the repository.
 * @property username The username for repository authentication, if required. Defaults to an empty string.
 * @property password The password for repository authentication, if required. Defaults to an empty string.
 */
class RepositoryDescription(
    val value: String,
    val username: String = "",
    val password: String = "",
)
