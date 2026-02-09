package build.util

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlinx.publisher.PublicationsExtension
import org.jetbrains.kotlinx.publisher.composeOfTaskOutputs

const val MAVEN_ARTIFACT_NAME_PREFIX = "kotlin-jupyter-"

/**
 * Helper function to create publications for shadowed configurations.
 *
 * It's expected that [project] has a `shadow` configuration
 * (you configured shadowJar task for it)
 *
 * [docShadowJars] are additional jars that should be added to the publication
 * apart from the main shadow jar designated by shadow configuration.
 *
 * Group id and version of the publication are inherited from the [PublicationsExtension]
 * of [project] or its parent projects.
 */
fun PublicationsExtension.addShadowPublications(
    project: Project,
    publicationNameBase: String,
    artifactId: String,
    description: String,
    docShadowJars: List<NamedDomainObjectProvider<out AbstractArchiveTask>>,
) {
    publication {
        publicationName.set(publicationNameBase + "Bin")
        this.artifactId.set(artifactId)
        this.description.set(description)
        composeOf {
            from(project.components["shadow"])
        }
    }

    publication {
        publicationName.set(publicationNameBase)
        this.artifactId.set(artifactId)
        this.description.set(description)
        composeOfTaskOutputs(docShadowJars)
    }
}
