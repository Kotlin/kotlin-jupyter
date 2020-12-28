package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.jupyter.api.TypeName

/**
 * Path for all Kotlin Jupyter related stuff in library JARs
 */
const val KOTLIN_JUPYTER_RESOURCES_PATH = "META-INF/kotlin-jupyter-libraries"

/**
 * Name of file in [KOTLIN_JUPYTER_RESOURCES_PATH] containing
 * information about library definitions and providers inside JAR
 */
const val KOTLIN_JUPYTER_LIBRARIES_FILE_NAME = "libraries.json"

/**
 * Entity inside library that may be instantiated to an object
 * of type [T]
 */
interface LibrariesInstantiable<T> {
    /**
     * FQN of this entity that is needed for instantiation
     */
    val fqn: TypeName
}

/**
 * Declaration of [LibraryDefinition] implementor
 *
 * @property fqn Implementor FQN
 */
@Serializable
data class LibrariesDefinitionDeclaration(
    override val fqn: TypeName,
) : LibrariesInstantiable<LibraryDefinition>

/**
 * Declaration of [LibraryDefinitionProducer] implementor
 *
 * @property fqn Implementor FQN
 */
@Serializable
data class LibrariesProducerDeclaration(
    override val fqn: TypeName,
) : LibrariesInstantiable<LibraryDefinitionProducer>

/**
 * Serialized form of this class instance is a correct content of
 * [KOTLIN_JUPYTER_LIBRARIES_FILE_NAME] file, and vice versa.
 */
@Serializable
data class LibrariesScanResult(
    val definitions: List<LibrariesDefinitionDeclaration> = emptyList(),
    val producers: List<LibrariesProducerDeclaration> = emptyList(),
)
