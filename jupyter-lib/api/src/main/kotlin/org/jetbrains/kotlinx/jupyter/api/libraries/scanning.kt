package org.jetbrains.kotlinx.jupyter.api.libraries

import kotlinx.serialization.Serializable

const val KOTLIN_JUPYTER_RESOURCES_PATH = "META-INF/kotlin-jupyter-libraries"
const val KOTLIN_JUPYTER_LIBRARIES_FILE_NAME = "libraries.json"

typealias FQN = String

interface LibrariesInstantiable<T> {
    val fqn: FQN
}

@Serializable
class LibrariesDefinitionDeclaration(
    override val fqn: FQN,
) : LibrariesInstantiable<LibraryDefinition>

@Serializable
class LibrariesProducerDeclaration(
    override val fqn: FQN,
) : LibrariesInstantiable<LibraryDefinitionProducer>

@Serializable
class LibrariesScanResult(
    val definitions: List<LibrariesDefinitionDeclaration> = emptyList(),
    val producers: List<LibrariesProducerDeclaration> = emptyList(),
)
