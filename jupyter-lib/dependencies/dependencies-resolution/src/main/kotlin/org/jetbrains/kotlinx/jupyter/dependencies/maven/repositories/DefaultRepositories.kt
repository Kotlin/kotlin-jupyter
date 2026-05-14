package org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories

import org.jetbrains.kotlinx.jupyter.dependencies.api.Repository

val CENTRAL_REPO =
    Repository(
        if (System.getenv("TEAMCITY_VERSION") != null) {
            "https://cache-redirector.jetbrains.com/maven-central/"
        } else {
            "https://repo.maven.apache.org/maven2/"
        },
    )
