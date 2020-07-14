package jupyter.kotlin

object JavaRuntime {
    private const val defaultVersion = 8

    val version by lazy {
        val defaultVersionStr = (if (defaultVersion <= 8) "1." else "") + "$defaultVersion"
        val version: String? = System.getProperty("java.version")

        val versionParts = version?.split('.')
        if (versionParts.isNullOrEmpty()){
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

    val versionAsInt by lazy {
        val regex = Regex("^(1\\.)?(\\d+)$")
        val match = regex.matchEntire(version)
        val plainVersion = match?.groupValues?.get(2)
        plainVersion?.toIntOrNull() ?: defaultVersion
    }

    fun assertVersion(message: String = "JRE version requirements are not satisfied", assertion: (Int) -> Boolean) {
        if (!assertion(versionAsInt)) {
            throw AssertionError(message)
        }
    }

    fun assertVersionAtLeast(minVersion: Int) =
            assertVersion("JRE version should be at least $minVersion") {
                it >= minVersion
            }

    fun assertVersionInRange(minVersion: Int, maxVersion: Int) =
            assertVersion("JRE version should be in range [$minVersion, $maxVersion]") {
                it in minVersion..maxVersion
            }
}
