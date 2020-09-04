package org.jetbrains.kotlin.jupyter.api

class KotlinKernelVersion private constructor(
        private val components: List<Int>
): Comparable<KotlinKernelVersion> {
    val major: Int get() = components[0]
    val minor: Int get() = components[1]
    val micro: Int get() = components[2]
    val build: Int? get() = components.getOrNull(3)
    val dev: Int? get() = components.getOrNull(4)

    override fun compareTo(other: KotlinKernelVersion): Int {
        for(i in 0 until maxSize(components, other.components)) {
            val thisC = components.getOrNull(i) ?: -1
            val otherC = other.components.getOrNull(i) ?: -1
            val compareRes = thisC.compareTo(otherC)
            if (compareRes == 0) continue
            return compareRes
        }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is KotlinKernelVersion && components == other.components
    }

    override fun hashCode(): Int {
        return components.hashCode()
    }

    override fun toString(): String {
        return buildString {
            append(major); append(SEP)
            append(minor); append(SEP)
            append(micro)
            build?.also {
                append(SEP); append(build)
                dev?.also {
                    append(SEP); append(DEV_PREFIX); append(dev)
                }
            }
        }
    }

    companion object {
        const val SEP = '.'
        const val DEV_PREFIX = "dev"

        fun KotlinKernelVersion?.toMaybeUnspecifiedString() = this?.toString() ?: "unspecified"

        fun from(string: String): KotlinKernelVersion? {
            val components = string.split(SEP)
            if (components.size < 3 || components.size > 5) return null

            val intComponents = mutableListOf<Int>()
            for (i in 0..2) {
                intComponents.add(components[i].toIntOrNull() ?: return null)
            }

            if (components.size >= 4) {
                intComponents.add(components[3].toIntOrNull() ?: return null)
                if(components.size >= 5) {
                    val devComponent = components[4]
                    if (!devComponent.startsWith(DEV_PREFIX)) return null
                    val devInt = devComponent.removePrefix(DEV_PREFIX).toIntOrNull() ?: return null
                    intComponents.add(devInt)
                }
            }

            if (!validateComponents(intComponents)) return null
            return KotlinKernelVersion(intComponents)
        }

        fun from(major: Int, minor: Int, micro: Int, build: Int? = null, dev: Int? = null): KotlinKernelVersion? {
            val components = mutableListOf<Int>().apply {
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
            return KotlinKernelVersion(components)
        }

        private fun validateComponents(components: List<Int>): Boolean {
            return components.size in 3..5 && components.all { it >= 0 }
        }

        private fun maxSize(a: Collection<*>, b: Collection<*>): Int {
            return if(a.size > b.size) a.size else b.size
        }
    }
}
