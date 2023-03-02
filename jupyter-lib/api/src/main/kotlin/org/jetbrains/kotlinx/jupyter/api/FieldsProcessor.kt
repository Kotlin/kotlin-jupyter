package org.jetbrains.kotlinx.jupyter.api

interface FieldsProcessor {
    fun register(handler: FieldHandler) = register(handler, ProcessingPriority.DEFAULT)

    /**
     * Register a handler for user-defined cell variables with the given processing priority
     * Handlers with the higher priority will be tried first
     * If priority is equal, handlers which were added later will be tried first
     * Note that only first accepted handler is applied to variable
     */
    fun register(handler: FieldHandler, priority: Int)

    /**
     * Unregister previously registered [handler]
     */
    fun unregister(handler: FieldHandler)

    /**
     * List all registered handlers with their priorities
     */
    fun registeredHandlers(): List<FieldHandlerWithPriority>
}
