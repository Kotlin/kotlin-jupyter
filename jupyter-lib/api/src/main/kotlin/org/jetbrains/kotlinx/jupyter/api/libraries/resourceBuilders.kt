package org.jetbrains.kotlinx.jupyter.api.libraries

import java.io.File
import java.util.logging.Logger

/**
 * Build a resource tree using [ResourcesBuilder]. The builder allows to construct `js` and `css` bundles. Each bundle
 * could contain multiple files or urls. Each of those could contain multiple fallback paths (for example if URL not found,
 * local resource is used.
 */
fun JupyterIntegration.Builder.resources(block: ResourcesBuilder.() -> Unit) {
    val resources = ResourcesBuilder().apply(block)
    resources.resources.forEach {
        resource(it)
    }
}

class ResourcesBuilder {
    internal val resources = ArrayList<LibraryResource>()

    class BundleBuilder {
        internal val bundles = ArrayList<ResourceFallbacksBundle>()

        private fun checkLocalPath(localPath: String) {
            if (!File(localPath).exists()) {
                Logger.getLogger("JupyterIntegration").warning("A resource with local file path '$localPath' not found.")
            }
        }

        private fun checkClassPath(classPath: String) {
            if (javaClass.getResource(classPath) == null) {
                Logger.getLogger("JupyterIntegration").warning("A resource with classpath '$classPath' not found.")
            }
        }

        /**
         * Create an url resource with optional embedding (governed by [embed] flag) with optional local file fallback and
         * a class-path fallback. If fallbacks are null, they are not used.
         */
        @OptIn(ExperimentalStdlibApi::class)
        fun url(url: String, localFallBack: String? = null, classpathFallBack: String? = null, embed: Boolean = false) {
            val libraryResource = ResourceFallbacksBundle(
                buildList {
                    if (embed) {
                        add(ResourceLocation(url, ResourcePathType.URL_EMBEDDED))
                    } else {
                        add(ResourceLocation(url, ResourcePathType.URL))
                    }
                    localFallBack?.let {
                        checkLocalPath(localFallBack)
                        add(ResourceLocation(localFallBack, ResourcePathType.LOCAL_PATH))
                    }
                    classpathFallBack?.let {
                        checkClassPath(classpathFallBack)
                        add(ResourceLocation(classpathFallBack, ResourcePathType.CLASSPATH_PATH))
                    }
                }
            )
            bundles.add(libraryResource)
        }

        /**
         * Use local resource from file
         */
        fun local(localPath: String) {
            checkLocalPath(localPath)
            bundles.add(
                ResourceFallbacksBundle(
                    listOf(ResourceLocation(localPath, ResourcePathType.LOCAL_PATH))
                )
            )
        }

        /**
         * Use Jar class-path resource
         */
        fun classPath(classPath: String) {
            checkClassPath(classPath)
            bundles.add(
                ResourceFallbacksBundle(
                    listOf(ResourceLocation(classPath, ResourcePathType.CLASSPATH_PATH))
                )
            )
        }
    }

    /**
     * Create a JS resource bundle
     */
    fun js(name: String, block: BundleBuilder.() -> Unit) {
        val bundles = BundleBuilder().apply(block).bundles
        resources.add(
            LibraryResource(
                name = name,
                type = ResourceType.JS,
                bundles = bundles
            )
        )
    }

    /**
     * Create a Css resource bundle
     */
    fun css(name: String, block: BundleBuilder.() -> Unit) {
        val bundles = BundleBuilder().apply(block).bundles
        resources.add(
            LibraryResource(
                name = name,
                type = ResourceType.CSS,
                bundles = bundles
            )
        )
    }
}
