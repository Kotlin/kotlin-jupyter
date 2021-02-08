package org.jetbrains.kotlinx.jupyter.publishing

class ArtifactPublication {
    var publicationName: String? = null
    var artifactId: String? = null
    var groupId: String? = NEXUS_PACKAGE_GROUP
    var bintrayPackageName: String? = null
    var bintrayDescription: String? = null
    var publishToSonatype: Boolean = true
}
