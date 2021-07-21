package build

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

const val DEFAULT_VERSION_CATALOG = "libs"
private const val VERSION_CATALOG_EXTENSION_PREFIX = "versionCatalogExtFor"

@Suppress("UnstableApiUsage")
class MyVersionCatalogsExtension(
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
            return catalog.findDependency(name).get()
        }
    }
}

private fun versionCatalogExtensionName(name: String) = VERSION_CATALOG_EXTENSION_PREFIX + name.capitalize()

fun Project.addVersionCatalogExtension(name: String = DEFAULT_VERSION_CATALOG) {
    extensions.add(versionCatalogExtensionName(name), MyVersionCatalogsExtension(this, name))
}

fun Project.versionCatalog(name: String): MyVersionCatalogsExtension = extensions.getOrCreate(versionCatalogExtensionName(name)) { MyVersionCatalogsExtension(this, name) }
val Project.defaultVersionCatalog get(): MyVersionCatalogsExtension = versionCatalog(DEFAULT_VERSION_CATALOG)


val MyVersionCatalogsExtension.Versions.devKotlin get() = get("kotlin")
val MyVersionCatalogsExtension.Versions.stableKotlin get() = get("stableKotlin")
val MyVersionCatalogsExtension.Versions.gradleKotlin get() = get("gradleKotlin")


val MyVersionCatalogsExtension.Dependencies.junitApi get() = get("test-junit-api")
val MyVersionCatalogsExtension.Dependencies.junitEngine get() = get("test-junit-engine")
val MyVersionCatalogsExtension.Dependencies.kotlinTest get() = get("kotlin-stable-test")