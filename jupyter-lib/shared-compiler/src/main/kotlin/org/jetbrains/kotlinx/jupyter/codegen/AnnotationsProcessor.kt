package org.jetbrains.kotlinx.jupyter.codegen

import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.GenerativeTypeHandler
import kotlin.reflect.KClass

interface AnnotationsProcessor {

    fun register(handler: GenerativeTypeHandler): Code

    fun process(kClass: KClass<*>): Code?
}
