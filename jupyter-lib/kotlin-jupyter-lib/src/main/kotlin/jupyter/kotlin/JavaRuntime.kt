package jupyter.kotlin

import org.jetbrains.kotlin.jupyter.api.RuntimeUtils

object JavaRuntime : RuntimeUtils {
    private const val defaultVersion = 8

    override val version by lazy {
        val defaultVersionStr = (if (defaultVersion <= 8) "1." else "") + "$defaultVersion"
        val version: String? = System.getProperty("java.version")

        val versionParts = version?.split('.')
        if (versionParts.isNullOrEmpty()) {
            defaultVersionStr
        } else if (versionParts[0] == "1") {
            if (versionParts.size > 1) {
                "1.${versionParts[1]}"
            } else {
                defaultVersionStr
            }
        } else {
            versionParts[0]
        }
    }

    override val versionAsInt by lazy {
        val regex = Regex("^(1\\.)?(\\d+)$")
        val match = regex.matchEntire(version)
        val plainVersion = match?.groupValues?.get(2)
        plainVersion?.toIntOrNull() ?: defaultVersion
    }

    override fun assertVersion(message: String, assertion: (Int) -> Boolean) {
        if (!assertion(versionAsInt)) {
            throw AssertionError(message)
        }
    }

    override fun assertVersionAtLeast(minVersion: Int) =
        assertVersion("JRE version should be at least $minVersion") {
            it >= minVersion
        }

    override fun assertVersionInRange(minVersion: Int, maxVersion: Int) =
        assertVersion("JRE version should be in range [$minVersion, $maxVersion]") {
            it in minVersion..maxVersion
        }
}
