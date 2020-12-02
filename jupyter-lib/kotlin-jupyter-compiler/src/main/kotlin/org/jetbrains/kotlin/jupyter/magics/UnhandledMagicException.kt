package org.jetbrains.kotlin.jupyter.magics

import org.jetbrains.kotlin.jupyter.common.ReplLineMagic

class UnhandledMagicException(
    magic: ReplLineMagic,
    handler: MagicsHandler,
) : Exception("Magic $magic is not handled by handler ${handler::class}")
