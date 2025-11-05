package org.jetbrains.kotlinx.jupyter.dependencies.maven.artifacts

data class GradleMavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String? = null,
    val extension: String? = null,
)

private val dependencyRegex =
    Regex(
        """^(?<groupId>[^:@'"\s]+):(?<artifactId>[^:@'"\s]+):(?<version>[^:@'"\s]+)(?::(?<classifier>[^@'"\s]+))?(?:@(?<extension>[^:'"\s]+))?$""",
    )

fun parseGradleCoordinatesString(str: String): GradleMavenCoordinates? {
    val match = dependencyRegex.matchEntire(str) ?: return null
    val (groupId, artifactId, version, classifier, extension) = match.destructured
    return GradleMavenCoordinates(
        groupId,
        artifactId,
        version,
        classifier.takeIf { it.isNotEmpty() },
        extension.takeIf { it.isNotEmpty() },
    )
}
