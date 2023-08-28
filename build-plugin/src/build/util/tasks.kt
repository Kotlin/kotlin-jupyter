package build.util

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlinx.publisher.ArtifactPublication


fun ArtifactPublication.composeOfTaskOutputs(tasks: List<NamedDomainObjectProvider<out AbstractArchiveTask>>) {
    composeOf {
        tasks.forEach { task -> artifact(task) }
    }
}
