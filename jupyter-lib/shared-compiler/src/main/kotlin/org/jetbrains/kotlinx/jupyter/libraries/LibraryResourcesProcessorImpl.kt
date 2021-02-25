package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceType
import java.util.EnumMap

class LibraryResourcesProcessorImpl : LibraryResourcesProcessor {
    private val processorMap = EnumMap<ResourceType, LibraryResourcesProcessor>(ResourceType::class.java)

    init {
        processorMap[ResourceType.JS] = JsLibraryResourcesProcessor()
        processorMap[ResourceType.CSS] = CssLibraryResourcesProcessor()
    }

    override fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        return processorMap.getValue(resource.type).wrapLibrary(resource, classLoader)
    }
}
