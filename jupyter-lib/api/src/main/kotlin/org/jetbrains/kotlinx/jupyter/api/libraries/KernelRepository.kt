package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.util.KernelRepositorySerializer
import org.jetbrains.kotlinx.jupyter.util.compareByProperties
import org.jetbrains.kotlinx.jupyter.util.replaceVariables

@Serializable(KernelRepositorySerializer::class)
data class KernelRepository(
    val path: String,
    val username: String? = null,
    val password: String? = null,
) : VariablesSubstitutionAware<KernelRepository>,
    Comparable<KernelRepository> {
    override fun replaceVariables(mapping: Map<String, String>): KernelRepository =
        KernelRepository(
            replaceVariables(path, mapping),
            username?.let { replaceVariables(it, mapping) },
            password?.let { replaceVariables(it, mapping) },
        )

    override fun compareTo(other: KernelRepository): Int =
        compareByProperties(
            other,
            KernelRepository::path,
            KernelRepository::username,
            KernelRepository::password,
        )
}
