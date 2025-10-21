package build.util

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlinx.publisher.PublicationsExtension
import org.jetbrains.kotlinx.publisher.composeOfTaskOutputs

const val MAVEN_ARTIFACT_NAME_PREFIX = "kotlin-jupyter-"

/**
 * Adds shadow publications to the provided PublicationsExtension. This is intended to create
 * specific publication configurations for binary and documentation artifacts.
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
        this.description.set(description)
        composeOfTaskOutputs(docShadowJars)
    }
}
