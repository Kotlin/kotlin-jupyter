package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.ints.shouldBeGreaterThan
import org.jetbrains.kotlinx.jupyter.libraries.ResourceLibraryDescriptorsProvider
import org.junit.jupiter.api.Test

class ResourceReadingTest {
    @Test
    fun `resource descriptors should be accessible from resources`() {
        val descriptorsProvider = ResourceLibraryDescriptorsProvider(testLoggerFactory)
        descriptorsProvider.getDescriptors().size shouldBeGreaterThan 5
    }
}
