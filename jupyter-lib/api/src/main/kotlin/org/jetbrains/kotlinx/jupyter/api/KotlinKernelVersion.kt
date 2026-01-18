package org.jetbrains.kotlinx.jupyter.api

private const val MAVEN_SNAPSHOT_SUFFIX = "-SNAPSHOT"
private const val PYPI_SNAPSHOT_SUFFIX = "+SNAPSHOT"

/**
 * Kotlin kernel version, with full specification, [Comparable] implementation and
 * serialization/deserialization
 */
class KotlinKernelVersion private constructor(
    private val components: List<Int>,
    val isSnapshot: Boolean = false,
) : Comparable<KotlinKernelVersion> {
    val major: Int = components[0]
    val minor: Int = components[1]
    val micro: Int = components[2]
    val build: Int? = components.getOrNull(3)
    val dev: Int? = components.getOrNull(4)

    override fun compareTo(other: KotlinKernelVersion): Int {
        for (i in 0 until maxSize(components, other.components)) {
            val thisC = components.getOrNull(i) ?: -1
            val otherC = other.components.getOrNull(i) ?: -1
            val compareRes = thisC.compareTo(otherC)
            if (compareRes != 0) return compareRes
        }
        return when {
            isSnapshot && !other.isSnapshot -> -1
            !isSnapshot && other.isSnapshot -> 1
            else -> 0
        }
    }

    override fun equals(other: Any?): Boolean =
        other is KotlinKernelVersion &&
            components == other.components &&
            isSnapshot == other.isSnapshot

    override fun hashCode(): Int {
        var result = components.hashCode()
        result = 31 * result + isSnapshot.hashCode()
        return result
    }

    override fun toString() = toPyPiVersion()

    fun toPyPiVersion() =
        buildString {
            buildMainVersionPart()
            build?.also {
                append(SEP)
                append(build)
                dev?.also {
                    append(SEP)
                    append(DEV_PREFIX)
                    append(dev)
                }
            }
            if (isSnapshot) {
                // PEP440 does not support `-SNAPSHOT` suffix the same way as Maven does.
                // To get as close as possible, we use a Local Version Identifier to signal
                // the same thing. In both cases, a SNAPSHOT version cannot be published
                // to the official repositories (Maven Central and PyPi).
                append(PYPI_SNAPSHOT_SUFFIX)
            }
        }

    fun toMavenVersion() =
        buildString {
            buildMainVersionPart()
            build?.also {
                append(DEV_SEP)
                append(build)
                dev?.also {
                    append(DEV_SEP)
                    append(dev)
                }
            }
            if (isSnapshot) {
                append(MAVEN_SNAPSHOT_SUFFIX)
            }
        }

    private fun StringBuilder.buildMainVersionPart() {
        append(major)
        append(SEP)
        append(minor)
        append(SEP)
        append(micro)
    }

    companion object {
        const val SEP = '.'
        const val DEV_SEP = '-'
        const val DEV_PREFIX = "dev"

        val STRING_VERSION_COMPARATOR =
            Comparator<String> { str1, str2 ->
                val v1 = fromMavenVersion(str1) ?: from(str1)
                val v2 = fromMavenVersion(str2) ?: from(str2)

                when {
                    v1 != null && v2 != null -> {
                        v1.compareTo(v2)
                    }
                    v1 != null -> 1
                    v2 != null -> -1
                    else -> str1.compareTo(str2)
                }
            }

        fun KotlinKernelVersion?.toMaybeUnspecifiedString() = this?.toString() ?: "unspecified"

        /**
         * Parse a Python version string into a [KotlinKernelVersion].
         * Returns `null` if the string is not a valid version string.
         */
        fun from(string: String): KotlinKernelVersion? {
            val (components, isSnapshot) = parseVersionComponents(string, PYPI_SNAPSHOT_SUFFIX)
            if (components.size !in 3..5) return null

            val intComponents = mutableListOf<Int>()
            for (i in 0..2) {
                intComponents.add(components[i].toIntOrNull() ?: return null)
            }

            if (components.size >= 4) {
                intComponents.add(components[3].toIntOrNull() ?: return null)
                if (components.size >= 5) {
                    val devComponent = components[4]
                    if (!devComponent.startsWith(DEV_PREFIX)) return null
                    val devInt = devComponent.removePrefix(DEV_PREFIX).toIntOrNull() ?: return null
                    intComponents.add(devInt)
                }
            }

            if (!validateComponents(intComponents)) return null
            return KotlinKernelVersion(intComponents, isSnapshot)
        }

        /**
         * Parse a Maven version string into a [KotlinKernelVersion].
         * Returns `null` if the string is not a valid version string.
         */
        fun fromMavenVersion(string: String): KotlinKernelVersion? {
            val (components, isSnapshot) = parseVersionComponents(string, MAVEN_SNAPSHOT_SUFFIX)
            if (components.size != 3) return null

            val intComponents = mutableListOf<Int>()
            for (i in 0..1) {
                intComponents.add(components[i].toIntOrNull() ?: return null)
            }

            val lastComponent = components[2]
            val lastIntComponents = lastComponent.split(DEV_SEP)
            for (component in lastIntComponents) {
                intComponents.add(component.toIntOrNull() ?: return null)
            }

            if (!validateComponents(intComponents)) return null
            return KotlinKernelVersion(intComponents, isSnapshot)
        }

        /**
         * Creates a [KotlinKernelVersion] from its individual components.
         */
        fun from(
            major: Int,
            minor: Int,
            micro: Int,
            build: Int? = null,
            dev: Int? = null,
            isSnapshot: Boolean = false,
        ): KotlinKernelVersion? {
            val components =
                mutableListOf<Int>().apply {
                    add(major)
                    add(minor)
                    add(micro)
                    build?.let {
                        add(build)
                        dev?.let {
                            add(dev)
                        }
                    }
                }

            if (!validateComponents(components)) return null
            return KotlinKernelVersion(components, isSnapshot)
        }

        private fun validateComponents(components: List<Int>): Boolean = components.size in 3..5 && components.all { it >= 0 }

        private fun maxSize(
            a: Collection<*>,
            b: Collection<*>,
        ): Int = if (a.size > b.size) a.size else b.size

        private data class VersionComponents(
            val components: List<String>,
            val isSnapshot: Boolean,
        )

        private fun parseVersionComponents(
            version: String,
            snapshotSuffix: String,
        ): VersionComponents {
            val isSnapshot = version.endsWith(snapshotSuffix)
            val components = version.removeSuffix(snapshotSuffix).split(SEP)
            return VersionComponents(components, isSnapshot)
        }
    }
}
