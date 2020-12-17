package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic

class UnhandledMagicException(
    magic: ReplLineMagic,
    handler: MagicsHandler,
) : Exception("Magic ${magic.nameForUser} is not handled by handler ${handler::class}")
