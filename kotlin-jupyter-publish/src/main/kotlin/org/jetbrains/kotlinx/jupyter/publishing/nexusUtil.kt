package org.jetbrains.kotlinx.jupyter.publishing

import io.codearte.gradle.nexus.NexusStagingExtension
import io.codearte.gradle.nexus.NexusStagingPlugin
import org.gradle.api.Project

const val NEXUS_PACKAGE_GROUP = "org.jetbrains.kotlinx"
const val NEXUS_REPO_URL = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"

fun getNexusUser(): String? {
    return System.getenv("SONATYPE_USER")
}

fun getNexusPassword(): String? {
    return System.getenv("SONATYPE_PASSWORD")
}

fun Project.applyNexusPlugin() {
    pluginManager.run {
        apply(NexusStagingPlugin::class.java)
    }

    extensions.configure<NexusStagingExtension>("nexusStaging") {
        username = getNexusUser()
        password = getNexusPassword()
        packageGroup = NEXUS_PACKAGE_GROUP
        repositoryDescription = "kotlin-jupyter project, v. ${project.version}"
        // serverUrl = NEXUS_REPO_URL
    }
}