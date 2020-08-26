package org.jetbrains.kotlin.jupyter.build

import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import java.nio.file.Path

fun makeTaskName(prefix: String, local: Boolean) = prefix + (if (local) "Local" else "Distrib")

fun makeDirs(path: Path) {
    val dir = path.toFile()
    if (!dir.exists()) {
        dir.mkdirs()
    }
}

fun getSubDir(dir: Path, vararg subDir: String): Path  = subDir.fold(dir, Path::resolve)

fun writeJson(json: Map<String, Any>, path: Path) {
    val str = JsonOutput.prettyPrint(JsonOutput.toJson(json))
    path.toFile().writeText(str, Charsets.UTF_8)
}

fun Path.deleteDir() = toFile().deleteRecursively()

fun Project.stringPropOrEmpty(name: String) = rootProject.findProperty(name) as String? ?: ""

interface AllOptions: BuildOptions, InstallOptions, DistribOptions
interface ProjectWithOptions : ProjectWithBuildOptions, ProjectWithInstallOptions, ProjectWithDistribOptions

class ProjectWithOptionsImpl(private val p: Project, private val opt: AllOptions):
        Project by p, InstallOptions by opt, DistribOptions by opt, BuildOptions by opt, ProjectWithOptions

fun readProperties(propertiesFile: Path): Map<String, String> =
    propertiesFile.toFile().readText().lineSequence()
            .map { it.split("=") }
            .filter { it.size == 2 }
            .map { it[0] to it[1] }.toMap()

fun <T> Project.getOrInitProperty(name: String, initializer: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return (if (extra.has(name)) extra[name] as? T else null)  ?: {
        val value = initializer()
        extra[name] = value
        value
    }()
}

fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
    return rootProject.findProperty(propertyName)?.let {
        when(it) {
            "true" -> true
            "false" -> false
            else -> null
        }
    } ?: default
}
