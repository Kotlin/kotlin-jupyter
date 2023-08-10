package build.util

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.PolymorphicDomainObjectContainer
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.RegisteringDomainObjectDelegateProviderWithTypeAndAction
import org.gradle.kotlin.dsl.registering
import java.util.concurrent.Callable


fun PolymorphicDomainObjectContainer<Task>.registeringShadowJarBy(
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
) = registeringShadowJarBaseBy(configuration) {
    configurations.add(configuration)
    this.action()
}


fun PolymorphicDomainObjectContainer<Task>.registeringSourcesShadowJarBy(
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
) = registeringShadowJarBaseBy(configuration) {
    from(project.files(Callable { configuration.resolveSources(project) }))
    archiveClassifier.set("sources")
    this.action()
}

fun PolymorphicDomainObjectContainer<Task>.registeringEmptySourcesJar(
): RegisteringDomainObjectDelegateProviderWithTypeAndAction<out PolymorphicDomainObjectContainer<Task>, out Jar> {
    return registering(Jar::class) {
        archiveBaseName.set("generic-empty")
        archiveClassifier.set("sources")
    }
}

fun PolymorphicDomainObjectContainer<Task>.registeringEmptyJavadocJar(
): RegisteringDomainObjectDelegateProviderWithTypeAndAction<out PolymorphicDomainObjectContainer<Task>, out Jar> {
    return registering(Jar::class) {
        archiveBaseName.set("generic-empty")
        archiveClassifier.set("javadoc")
    }
}

private fun PolymorphicDomainObjectContainer<Task>.registeringShadowJarBaseBy(
    configuration: Configuration,
    action: ShadowJar.() -> Unit = {}
): RegisteringDomainObjectDelegateProviderWithTypeAndAction<out PolymorphicDomainObjectContainer<Task>, out ShadowJar> {
    val tasks = this
    return registering(ShadowJar::class) {
        val shadowJarTask = tasks.findByName("shadowJar")
        shadowJarTask?.let { mustRunAfter(it) }
        archiveBaseName.set(configuration.name)
        this.action()
    }
}
