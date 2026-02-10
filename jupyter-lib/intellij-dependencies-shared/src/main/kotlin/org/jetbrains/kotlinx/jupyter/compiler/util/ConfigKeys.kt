package org.jetbrains.kotlinx.jupyter.compiler.util

import kotlin.script.experimental.jvm.JvmScriptEvaluationConfigurationKeys
import kotlin.script.experimental.util.PropertiesCollection

val JvmScriptEvaluationConfigurationKeys.actualClassLoader by PropertiesCollection.key<ClassLoader?>(isTransient = true)
