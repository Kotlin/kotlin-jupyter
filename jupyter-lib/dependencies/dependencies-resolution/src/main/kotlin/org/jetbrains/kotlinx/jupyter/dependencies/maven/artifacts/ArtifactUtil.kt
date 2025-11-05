package org.jetbrains.kotlinx.jupyter.dependencies.maven.artifacts

import org.jetbrains.amper.dependency.resolution.MavenCoordinates

internal fun String.toMavenArtifact(): MavenCoordinates? {
    val gradleMavenCoordinates = parseGradleCoordinatesString(this) ?: return null
    return with(gradleMavenCoordinates) {
        MavenCoordinates(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = classifier,
        )
    }
}
