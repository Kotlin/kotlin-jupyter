package org.jetbrains.kotlinx.jupyter.test

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorGlobalOptions
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptorGlobalOptions
import org.junit.jupiter.api.Test
import java.io.File

class DescriptorGlobalOptionsTest {
    @Suppress("SameParameterValue")
    private fun loadOptions(fileName: String): LibraryDescriptorGlobalOptions {
        val path = "src/test/testData/globalDescriptorOptions/$fileName"
        val text = File(path).readText()
        return parseLibraryDescriptorGlobalOptions(text)
    }

    @Test
    fun test1() {
        with(loadOptions("global1.options")) {
            isPropertyIgnored("version-renovate-hint").shouldBeTrue()
            isPropertyIgnored("version-renovate-hint-abc").shouldBeFalse()
            isPropertyIgnored("renovate-hint").shouldBeFalse()
            isPropertyIgnored("version").shouldBeFalse()

            isPropertyIgnored("abc-axzy").shouldBeFalse()
            isPropertyIgnored("abc-a").shouldBeTrue()
        }
    }
}
