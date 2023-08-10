package build.util

import build.SingleInstanceExtensionCompanion
import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.kotlin.dsl.exclude
import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import java.io.File
import java.nio.file.Path
import java.util.Optional

val BUILD_LIBRARIES = LibraryDescriptorsManager.getInstance()

fun makeTaskName(prefix: String, local: Boolean) = prefix + (if (local) "Local" else "Distrib")

fun makeDirs(dir: File) {
    if (!dir.exists()) {
        dir.mkdirs()
    }
}

fun getSubDir(dir: Path, vararg subDir: String): Path = subDir.fold(dir, Path::resolve)

fun writeJson(json: Map<String, Any>, path: File) {
    val str = JsonOutput.prettyPrint(JsonOutput.toJson(json))
    path.writeText(str, Charsets.UTF_8)
}

fun Path.deleteDir() = toFile().deleteRecursively()

fun <T> Project.typedProperty(name: String): T {
    @Suppress("UNCHECKED_CAST")
    return findProperty(name) as T
}

fun readProperties(propertiesFile: File): Map<String, String> =
    propertiesFile.readText().lineSequence()
        .map { it.split("=") }
        .filter { it.size == 2 }
        .map { it[0] to it[1] }.toMap()

val Any.booleanValueOrNull get() = when (this) {
    "true", true -> true
    "false", false -> false
    else -> null
}

val Any.booleanValue get() = booleanValueOrNull ?: throw IllegalArgumentException("Cannot cast $this to boolean")

fun Task.taskTempFile(path: String): File {
    return temporaryDir.resolve(path).apply {
        createNewFile()
        deleteOnExit()
    }
}

@Suppress("unused")
fun ModuleDependency.excludeKotlinDependencies(vararg dependencyNames: String) {
    dependencyNames.forEach {
        exclude("org.jetbrains.kotlin", "kotlin-$it")
    }
}

fun ModuleDependency.excludeStandardKotlinDependencies() {
    excludeKotlinDependencies(
        "stdlib",
        "stdlib-common",
        "kotlin-stdlib-jdk7",
        "kotlin-stdlib-jdk8"
    )
}

fun <T> Optional<T>.getOrNull(): T? {
    var result: T? = null
    ifPresent { result = it }
    return result
}

inline fun <reified T : Any> ExtensionContainer.getOrCreate(name: String, initializer: () -> T): T {
    return (findByName(name) as? T) ?: initializer().also { ext -> add(name, ext) }
}

inline fun <reified T : Any> Project.getOrCreateExtension(extension: SingleInstanceExtensionCompanion<T>): T {
    return extensions.getOrCreate(extension.name) { extension.createInstance(this) }
}

@OptIn(ExperimentalStdlibApi::class)
fun buildProperties(builderAction: MutableList<Pair<String, String>>.() -> Unit): List<Pair<String, String>> {
    return buildList(builderAction)
}

fun isNonStableVersion(version: String): Boolean {
    return listOf("dev", "M", "alpha", "beta", "-").any { version.contains(it) }
}
