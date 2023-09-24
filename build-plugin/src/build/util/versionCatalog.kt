package build.util

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

private const val DEFAULT_VERSION_CATALOG = "libs"
private const val VERSION_CATALOG_EXTENSION_PREFIX = "versionCatalogExtFor"

@Suppress("UnstableApiUsage")
class NamedVersionCatalogsExtension(
    private val project: Project,
    private val catalogName: String,
) {

    private val catalog = run {
        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        catalogs.named(catalogName)
    }

    val versions = Versions()
    inner class Versions {
        fun get(ref: String): String {
            return catalog.findVersion(ref).get().requiredVersion
        }

        fun getOrNull(ref: String): String? {
            return catalog.findVersion(ref).getOrNull()?.requiredVersion
        }
    }

    val dependencies = Dependencies()
    inner class Dependencies {
        fun get(name: String): Provider<MinimalExternalModuleDependency> {
            return catalog.findLibrary(name).get()
        }
    }
}

private fun versionCatalogExtensionName(name: String) = VERSION_CATALOG_EXTENSION_PREFIX + name.titleCaseFirstChar()

fun Project.versionCatalog(name: String): NamedVersionCatalogsExtension = extensions.getOrCreate(versionCatalogExtensionName(name)) { NamedVersionCatalogsExtension(this, name) }
val Project.defaultVersionCatalog get(): NamedVersionCatalogsExtension = versionCatalog(DEFAULT_VERSION_CATALOG)

val NamedVersionCatalogsExtension.Versions.devKotlin get() = get("kotlin")
val NamedVersionCatalogsExtension.Versions.stableKotlin get() = get("stableKotlin")
val NamedVersionCatalogsExtension.Versions.gradleKotlin get() = get("gradleKotlin")
val NamedVersionCatalogsExtension.Versions.ktlint get() = get("ktlint")
val NamedVersionCatalogsExtension.Versions.ksp get() = get("ksp")
val NamedVersionCatalogsExtension.Versions.jvmTarget get() = get("jvmTarget")

val NamedVersionCatalogsExtension.Dependencies.junitApi get() = get("test.junit.api")
val NamedVersionCatalogsExtension.Dependencies.junitEngine get() = get("test.junit.engine")
val NamedVersionCatalogsExtension.Dependencies.kotlinTest get() = get("kotlin.stable.test")
val NamedVersionCatalogsExtension.Dependencies.kotlintestAssertions get() = get("test.kotlintest.assertions")
