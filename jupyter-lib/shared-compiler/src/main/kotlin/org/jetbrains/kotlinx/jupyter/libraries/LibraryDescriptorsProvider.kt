package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.config.librariesFromResources

interface LibraryDescriptorsProvider {
    fun getDescriptors(): Map<String, LibraryDescriptor>
}

open class ResourceLibraryDescriptorsProvider : LibraryDescriptorsProvider {
    override fun getDescriptors(): Map<String, LibraryDescriptor> {
        return librariesFromResources
    }
}
