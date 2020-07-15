package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.DependsOn
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

val classpath = scriptCompilationClasspathFromContext(
        "jupyter-lib",
        "kotlin-stdlib",
        "kotlin-reflect",
        "kotlin-script-runtime",
        classLoader = DependsOn::class.java.classLoader
)
