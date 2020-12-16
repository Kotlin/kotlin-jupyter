package org.jetbrains.kotlin.jupyter.compiler

import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.JvmGetScriptingClass

class JupyterScriptClassGetter(
    private val previousScriptClassesProvider: PreviousScriptClassesProvider
) : GetScriptingClassByClassLoader {
    private val getScriptingClass = JvmGetScriptingClass()

    private val lastClassLoader
        get() = previousScriptClassesProvider.get().lastOrNull()?.fromClass?.java?.classLoader

    override fun invoke(
        classType: KotlinType,
        contextClass: KClass<*>,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        return getScriptingClass(classType, lastClassLoader ?: contextClass.java.classLoader, hostConfiguration)
    }

    override fun invoke(
        classType: KotlinType,
        contextClassLoader: ClassLoader?,
        hostConfiguration: ScriptingHostConfiguration
    ): KClass<*> {
        return getScriptingClass(classType, lastClassLoader ?: contextClassLoader, hostConfiguration)
    }
}
