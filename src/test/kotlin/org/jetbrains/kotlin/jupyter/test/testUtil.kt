package org.jetbrains.kotlin.jupyter.test

import jupyter.kotlin.DependsOn
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContext

val classpath = scriptCompilationClasspathFromContext("jupyter-lib", classLoader = DependsOn::class.java.classLoader)
