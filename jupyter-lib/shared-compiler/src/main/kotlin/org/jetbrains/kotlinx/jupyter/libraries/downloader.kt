package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.common.LibraryDescriptorsManager
import org.jetbrains.kotlinx.jupyter.config.errorForUser
import org.jetbrains.kotlinx.jupyter.config.getLogger

val KERNEL_LIBRARIES = LibraryDescriptorsManager.getInstance(
    getLogger(),
) { logger, message, exception ->
    logger.errorForUser(message = message, throwable = exception)
}
