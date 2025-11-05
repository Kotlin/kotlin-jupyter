package org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories

import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.Repository

val amperRepositoryComparator =
    compareBy<Repository> {
        // Maven Local goes first
        it !is MavenLocal
    }.thenBy {
        // Maven Central goes last
        (it as? MavenRepository)?.url == CENTRAL_REPO.value
    }.thenBy {
        // Otherwise, sort by URL
        (it as? MavenRepository)?.url
    }
