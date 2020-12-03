package org.jetbrains.kotlin.jupyter.codegen

import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler

/**
 * Processes REPL local variables (implemented as properties in script classes) and converts them into new types using code generation
 */
interface TypeProvidersProcessor {

    /**
     * Registers new type handler together with a callback code, that generates code for new type
     * Whenever a variable of corresponding type is detected in script classes, callback is executed and generated code is scheduled for execution inside REPL context
     *
     * Callback code syntax example:
     * $it.generateWrapper()
     *
     * `$it` will be replaced with variable name
     * Result of callback must be of type List<String>, that contains a list of code snippets to be executed for type conversion
     * This list consists of a number of declarations followed by actual code for type conversion
     * Declarations can have a `###` placeholder that will be replaced with auto-incremented ID, if the code snippet is new, or will reuse existing ID, if exactly the same code snippet has already been declared
     */
    fun register(handler: GenerativeTypeHandler): Code

    /**
     * Processes local variables and generates code snippets that perform type conversions
     */
    fun process(): List<Code>
}
