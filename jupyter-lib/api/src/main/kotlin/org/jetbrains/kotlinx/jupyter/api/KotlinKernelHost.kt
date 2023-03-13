package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.libraries.CodeExecution
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition

/**
 * Interface representing kernel engine, the core facility for compiling and executing code snippets
 */
interface KotlinKernelHost {
    /**
     * Try to display the given value. It is only displayed if it's an instance of [Renderable]
     * or may be converted to it
     *
     * Left for binary compatibility
     */
    @Deprecated("Use full version instead", ReplaceWith("display(value, null)"))
    fun display(value: Any) = display(value, null)

    /**
     * Try to display the given value. It is only displayed if it's an instance of [Renderable]
     * or may be converted to it
     */
    fun display(value: Any, id: String? = null)

    /**
     * Updates display data with given [id] with the new [value]
     */
    fun updateDisplay(value: Any, id: String? = null)

    /**
     * Schedules execution of the given [execution] after the completing of execution of the current cell
     */
    fun scheduleExecution(execution: ExecutionCallback<*>)

    fun scheduleExecution(execution: Code) = scheduleExecution(CodeExecution(execution).toExecutionCallback())

    /**
     * Executes code immediately. Note that it may lead to breaking the kernel state in some cases
     */
    fun execute(code: Code): FieldValue

    fun addLibrary(library: LibraryDefinition) = addLibraries(listOf(library))

    /**
     * Adds new libraries via their definition. Fully interchangeable with `%use` approach
     */
    fun addLibraries(libraries: Collection<LibraryDefinition>)

    /**
     * Says whether this [typeName] should be loaded as integration based on loaded libraries.
     * `null` means that loaded libraries don't care about this [typeName].
     */
    fun acceptsIntegrationTypeName(typeName: String): Boolean?

    /**
     * Loads Kotlin standard artifacts (org.jetbrains.kotlin:kotlin-$name:$version)
     *
     * @param artifacts Names of the artifacts substituted to the above line
     * @param version Version of the artifacts to load. Current Kotlin version will be used by default
     */
    fun loadKotlinArtifacts(artifacts: Collection<String>, version: String? = null)

    /**
     * Loads Kotlin standard library extensions for a current JDK
     *
     * @param version Version of the artifact to load. Current Kotlin version will be used by default
     */
    fun loadStdlibJdkExtensions(version: String? = null)

    /**
     * Declares global variables for notebook
     */
    fun declare(variables: Iterable<VariableDeclaration>)

    val lastClassLoader: ClassLoader
}
