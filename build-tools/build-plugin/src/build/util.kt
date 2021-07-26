package build

import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.extra
import org.gradle.tooling.BuildException
import java.io.File
import java.nio.file.Path
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

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

class ProjectPropertyDelegate<T>(
    private val project: Project,
    private val type: KType,
    private val projectPropertyName: String? = null,
    private val default: T? = null,
) {
    private val kClass = type.classifier as KClass<*>
    private val isNullable = type.isMarkedNullable

    private var externallyDefined: T? = null
    private var externallySet = false

    private var projectDefined: T? = null
    private var calculated = false

    @Suppress("UNCHECKED_CAST")
    private fun calculate(propertyName: String): T {
        val value: String? = project.typedProperty(propertyName)
        return if (value == null) {
            default ?: if (isNullable) null as T
            else throw BuildException("Property $propertyName is not set", null)
        } else {
            when (kClass) {
                String::class -> value as T
                Boolean::class -> value.booleanValue as T
                Int::class -> value.toInt() as T
                else -> throw BuildException("Unsupported option type: $type", null)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return if (externallySet) {
            externallyDefined as T
        } else {
            if (calculated) {
                projectDefined as T
            } else {
                calculated = true
                val res = calculate(projectPropertyName ?: property.name)
                projectDefined = res
                res
            }
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        externallySet = true
        externallyDefined = value
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> Project.prop(
    projectPropertyName: String? = null,
    default: T? = null
) = ProjectPropertyDelegate(this, typeOf<T>(), projectPropertyName, default)

fun Project.stringPropOrEmpty(name: String) = rootProject.findProperty(name) as String? ?: ""

fun readProperties(propertiesFile: File): Map<String, String> =
    propertiesFile.readText().lineSequence()
        .map { it.split("=") }
        .filter { it.size == 2 }
        .map { it[0] to it[1] }.toMap()

fun <T> Project.getOrInitProperty(name: String, initializer: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return (if (extra.has(name)) extra[name] as? T else null) ?: run {
        val value = initializer()
        extra[name] = value
        value
    }
}

val Any.booleanValueOrNull get() = when (this) {
    "true", true -> true
    "false", false -> false
    else -> null
}

val Any.booleanValue get() = booleanValueOrNull ?: throw IllegalArgumentException("Cannot cast $this to boolean")

fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
    return rootProject.findProperty(propertyName)?.booleanValueOrNull ?: default
}

@Suppress("unused")
fun ModuleDependency.excludeKotlinDependencies(vararg dependencyNames: String) {
    dependencyNames.forEach {
        exclude("org.jetbrains.kotlin", "kotlin-$it")
    }
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
