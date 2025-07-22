package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.JREInfoProvider
import org.jetbrains.kotlinx.jupyter.api.jvm.JavaVersion

object JavaRuntime : JREInfoProvider {
    override val javaVersion =
        JavaVersion(8) {
            System.getProperty("java.version")
        }

    @Deprecated("Use javaVersion instead", replaceWith = ReplaceWith("javaVersion.versionString"))
    override val version get() = javaVersion.versionString

    @Deprecated("Use javaVersion instead", replaceWith = ReplaceWith("javaVersion.versionInteger"))
    override val versionAsInt get() = javaVersion.versionInteger

    override fun assertVersion(
        message: String,
        condition: (Int) -> Boolean,
    ) {
        if (!condition(javaVersion.versionInteger)) {
            throw AssertionError(message)
        }
    }

    override fun assertVersionAtLeast(minVersion: Int) =
        assertVersion("JRE version should be at least $minVersion") {
            it >= minVersion
        }

    override fun assertVersionInRange(
        minVersion: Int,
        maxVersion: Int,
    ) = assertVersion("JRE version should be in range [$minVersion, $maxVersion]") {
        it in minVersion..maxVersion
    }
}
