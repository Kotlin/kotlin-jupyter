package build.util

import org.gradle.api.Project
import org.gradle.kotlin.dsl.extra
import org.gradle.tooling.BuildException
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

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

inline fun <reified T> Project.prop(
    projectPropertyName: String? = null,
    default: T? = null
) = ProjectPropertyDelegate(this, typeOf<T>(), projectPropertyName, default)

fun Project.stringPropOrEmpty(name: String) = rootProject.findProperty(name) as String? ?: ""

fun <T> Project.getOrInitProperty(name: String, initializer: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return (if (extra.has(name)) extra[name] as? T else null) ?: run {
        val value = initializer()
        extra[name] = value
        value
    }
}

fun Project.getFlag(propertyName: String, default: Boolean = false): Boolean {
    return rootProject.findProperty(propertyName)?.booleanValueOrNull ?: default
}
