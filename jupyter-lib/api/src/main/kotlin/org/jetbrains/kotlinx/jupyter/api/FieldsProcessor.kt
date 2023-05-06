package org.jetbrains.kotlinx.jupyter.api

interface FieldsProcessor : ExtensionsProcessor<FieldHandler> {
    /**
     * List all registered handlers with their priorities
     */
    fun registeredHandlers(): List<FieldHandlerWithPriority>
}
