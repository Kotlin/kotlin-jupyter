package org.jetbrains.kotlinx.jupyter.publishing

class ArtifactPublication {
    var publicationName: String? = null
    var artifactId: String? = null
    var groupId: String? = NEXUS_PACKAGE_GROUP
    var packageName: String? = null
    var description: String? = null
    var publishToSonatype: Boolean = true
}
