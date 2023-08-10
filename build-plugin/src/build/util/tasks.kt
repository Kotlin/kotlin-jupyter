package build.util

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlinx.publisher.ArtifactPublication
import java.util.concurrent.Callable


private fun PolymorphicDomainObjectContainer<Task>.registerShadowJarBy(
    name: String,
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
) = registerShadowJarBaseBy(name, configuration) {
    configurations.add(configuration)
    this.action()
}


private fun PolymorphicDomainObjectContainer<Task>.registerSourcesShadowJarBy(
    name: String,
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
) = registerShadowJarBaseBy(name, configuration) {
    from(project.files(Callable { configuration.resolveSources(project) }))
    archiveClassifier.set("sources")
    this.action()
}

private fun PolymorphicDomainObjectContainer<Task>.registerEmptySourcesJar(
    name: String,
    configuration: Configuration,
): NamedDomainObjectProvider<Jar> {
    return register(name, Jar::class) {
        archiveBaseName.set(configuration.name)
        archiveClassifier.set("sources")
    }
}

private fun PolymorphicDomainObjectContainer<Task>.registerEmptyJavadocJar(
    name: String,
    configuration: Configuration,
): NamedDomainObjectProvider<Jar> {
    return register(name, Jar::class) {
        archiveBaseName.set(configuration.name)
        archiveClassifier.set("javadoc")
    }
}

private fun PolymorphicDomainObjectContainer<Task>.registerShadowJarBaseBy(
    name: String,
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
): NamedDomainObjectProvider<ShadowJar> {
    val tasks = this
    return register(name, ShadowJar::class) {
        val shadowJarTask = tasks.findByName("shadowJar")
        shadowJarTask?.let { mustRunAfter(it) }
        archiveBaseName.set(configuration.name)
        this.action()
    }
}

fun PolymorphicDomainObjectContainer<Task>.registerShadowJarTasksBy(
    configuration: Configuration,
    withSources: Boolean = true,
): List<NamedDomainObjectProvider<out AbstractArchiveTask>> {
    val baseName = configuration.name

    val jarTask = registerShadowJarBy(baseName + "Jar", configuration)
    val sourcesJarTask = if (withSources) {
        registerSourcesShadowJarBy(baseName + "SourcesJar", configuration)
    } else {
        registerEmptySourcesJar(baseName + "SourcesJar", configuration)
    }
    val javadocJarTask = registerEmptyJavadocJar(baseName + "JavadocJar", configuration)

    return listOf(jarTask, sourcesJarTask, javadocJarTask)
}

fun ArtifactPublication.composeOfTaskOutputs(tasks: List<NamedDomainObjectProvider<out AbstractArchiveTask>>) {
    composeOf {
        tasks.forEach { task -> artifact(task) }
    }
}
