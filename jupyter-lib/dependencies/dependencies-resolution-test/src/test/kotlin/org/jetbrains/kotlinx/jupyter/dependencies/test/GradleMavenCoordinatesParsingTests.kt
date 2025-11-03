package org.jetbrains.kotlinx.jupyter.dependencies.test

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.jupyter.dependencies.parseGradleCoordinatesString
import org.junit.jupiter.api.Test

class GradleMavenCoordinatesParsingTests {
    @Test
    fun `parses basic group-artifact-version`() {
        val coords = parseGradleCoordinatesString("org.jetbrains.kotlin:kotlin-stdlib:1.9.0").shouldNotBeNull()
        coords.groupId shouldBe "org.jetbrains.kotlin"
        coords.artifactId shouldBe "kotlin-stdlib"
        coords.version shouldBe "1.9.0"
        coords.classifier.shouldBeNull()
        coords.extension.shouldBeNull()
    }

    @Test
    fun `parses with classifier`() {
        val coords = parseGradleCoordinatesString("com.example:lib:2.0.1:all").shouldNotBeNull()
        coords.groupId shouldBe "com.example"
        coords.artifactId shouldBe "lib"
        coords.version shouldBe "2.0.1"
        coords.classifier shouldBe "all"
        coords.extension.shouldBeNull()
    }

    @Test
    fun `parses with extension`() {
        val coords = parseGradleCoordinatesString("com.example:lib:2.0.1@pom").shouldNotBeNull()
        coords.groupId shouldBe "com.example"
        coords.artifactId shouldBe "lib"
        coords.version shouldBe "2.0.1"
        coords.classifier.shouldBeNull()
        coords.extension shouldBe "pom"
    }

    @Test
    fun `parses with classifier and extension`() {
        val coords = parseGradleCoordinatesString("com.example:lib:2.0.1:linux@aar").shouldNotBeNull()
        coords.groupId shouldBe "com.example"
        coords.artifactId shouldBe "lib"
        coords.version shouldBe "2.0.1"
        coords.classifier shouldBe "linux"
        coords.extension shouldBe "aar"
    }

    @Test
    fun `returns null on invalid inputs`() {
        parseGradleCoordinatesString("  'com.ex:lib:1.0'  ").shouldBeNull()
        parseGradleCoordinatesString("\"com.ex:lib:1.0\"").shouldBeNull()
        parseGradleCoordinatesString("com.ex:lib").shouldBeNull()
        parseGradleCoordinatesString("com.ex: lib :1.0").shouldBeNull()
        parseGradleCoordinatesString("com.ex:lib:1.0:").shouldBeNull()
        parseGradleCoordinatesString(":").shouldBeNull()
    }
}
