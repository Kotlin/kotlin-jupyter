package org.jetbrains.kotlinx.jupyter.api.jvm

class JavaVersion(
    defaultVersion: Int,
    versionStringProvider: () -> String? = { null },
) {
    val versionString by lazy {
        val defaultVersionStr = (if (defaultVersion <= 8) "1." else "") + "$defaultVersion"
        val version: String? = versionStringProvider()

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

    val versionInteger by lazy {
        val regex = Regex("^(1\\.)?(\\d+)(\\..*)?$")
        val match = regex.matchEntire(versionString)
        val plainVersion = match?.groupValues?.get(2)
        plainVersion?.toIntOrNull() ?: defaultVersion
    }

    override fun toString(): String = versionString
}
