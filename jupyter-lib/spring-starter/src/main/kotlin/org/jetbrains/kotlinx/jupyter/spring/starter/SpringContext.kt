package org.jetbrains.kotlinx.jupyter.spring.starter

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class SpringContext : ApplicationContextAware {
    companion object {
        private var context: ApplicationContext? = null

        fun getContext(): ApplicationContext? = context
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        context = applicationContext
    }
}

@Suppress("unused")
val springContext: ApplicationContext get() = SpringContext.getContext() ?: error("Spring context is not initialized")
