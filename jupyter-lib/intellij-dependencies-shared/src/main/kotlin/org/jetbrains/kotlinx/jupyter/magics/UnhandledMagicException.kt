package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.exceptions.ReplPreprocessingException

class UnhandledMagicException(
    magic: ReplLineMagic,
    handler: MagicsHandler,
) : ReplPreprocessingException("Magic ${magic.nameForUser} is not handled by handler ${handler::class}")
