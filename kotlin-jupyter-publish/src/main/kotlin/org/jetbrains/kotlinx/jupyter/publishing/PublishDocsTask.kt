package org.jetbrains.kotlinx.jupyter.publishing

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.property

abstract class PublishDocsTask: DefaultTask() {
    @Input
    val docsRepoUrl: Property<String> = project.objects.property()
}
