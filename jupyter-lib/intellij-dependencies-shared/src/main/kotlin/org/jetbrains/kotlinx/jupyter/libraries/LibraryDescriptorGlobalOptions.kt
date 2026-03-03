package org.jetbrains.kotlinx.jupyter.libraries

interface LibraryDescriptorGlobalOptions {
    fun isPropertyIgnored(propertyName: String): Boolean
}

object DefaultLibraryDescriptorGlobalOptions : LibraryDescriptorGlobalOptions {
    override fun isPropertyIgnored(propertyName: String): Boolean {
        // We exclude hints for Renovate CI tool, see https://github.com/Kotlin/kotlin-jupyter-libraries/pull/201
        // Could be removed later
        return propertyName.endsWith("-renovate-hint")
    }
}
