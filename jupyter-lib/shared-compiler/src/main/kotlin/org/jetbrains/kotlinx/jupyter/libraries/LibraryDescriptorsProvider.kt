package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.config.descriptorOptionsFromResources
import org.jetbrains.kotlinx.jupyter.config.librariesFromResources

interface LibraryDescriptorsProvider {
    fun getDescriptors(): Map<String, LibraryDescriptor>

    fun getDescriptorForVersionsCompletion(fullName: String): LibraryDescriptor? {
        return getDescriptors()[fullName]
    }

    fun getDescriptorGlobalOptions(): LibraryDescriptorGlobalOptions = DefaultLibraryDescriptorGlobalOptions
}

open class ResourceLibraryDescriptorsProvider : LibraryDescriptorsProvider {
    override fun getDescriptors(): Map<String, LibraryDescriptor> {
        return librariesFromResources
    }

    override fun getDescriptorGlobalOptions(): LibraryDescriptorGlobalOptions {
        return descriptorOptionsFromResources
    }
}
