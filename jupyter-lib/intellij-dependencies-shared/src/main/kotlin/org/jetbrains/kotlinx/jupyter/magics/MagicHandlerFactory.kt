package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.magics.contexts.MagicHandlerContext
import org.jetbrains.kotlinx.jupyter.magics.contexts.getContext
import kotlin.reflect.KClass

/**
 * Factory for creating [MagicsHandler]s.
 * If the context for the handler creation is unsufficient,
 * the factory should return null.
 */
interface MagicHandlerFactory {
    fun createIfApplicable(context: MagicHandlerContext): MagicsHandler?
}

open class MagicHandlerFactoryImpl(
    private val factory: (context: MagicHandlerContext) -> MagicsHandler,
    private val requiredContexts: List<KClass<out MagicHandlerContext>>,
) : MagicHandlerFactory {
    override fun createIfApplicable(context: MagicHandlerContext): MagicsHandler? {
        val isApplicable =
            requiredContexts.all { contextClass ->
                context.getContext(contextClass) != null
            }
        if (!isApplicable) return null
        return factory(context)
    }
}
