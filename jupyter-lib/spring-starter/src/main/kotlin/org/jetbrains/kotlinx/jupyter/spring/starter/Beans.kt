package org.jetbrains.kotlinx.jupyter.spring.starter

import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import jupyter.kotlin.USE
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

@Suppress("unused")
fun ScriptTemplateWithDisplayHelpers.declareAllBeans() {
    USE {
        declareAllBeansInLibrary()
    }
}

@Suppress("unused")
fun JupyterIntegration.Builder.declareAllBeansInLibrary() {
    declareBeansByNames(springContext.beanDefinitionNames.toList())
}

@Suppress("unused")
fun JupyterIntegration.Builder.declareBeansByClasses(beanClasses: Iterable<KClass<*>>) {
    val beanInstances =
        buildMap {
            beanClasses.forEachSafe { beanClass ->
                val className = beanClass.simpleName ?: return@forEachSafe
                val bean = springContext.getBean(beanClass.java)
                val beanName = className.replaceFirstChar { it.lowercase(Locale.getDefault()) }

                put(beanName, VariableDeclaration(beanName, bean, beanClass.starProjectedType, isMutable = false))
            }
        }

    declareBeanInstances(beanInstances)
}

fun JupyterIntegration.Builder.declareBeansByNames(beanNames: Iterable<String>) {
    val beanInstances =
        buildMap {
            beanNames.forEachSafe { beanName ->
                val varName = beanName.substringAfterLast(".")
                if (varName.contains("$")) return@forEachSafe

                val beanClass = springContext.getType(beanName)?.kotlin ?: return@forEachSafe

                val qualifiedName = beanClass.qualifiedName ?: return@forEachSafe
                if (qualifiedName.contains("$") || qualifiedName.startsWith("com.sun.")) return@forEachSafe
                if (beanClass.visibility != KVisibility.PUBLIC) return@forEachSafe

                val bean = springContext.getBean(beanClass.java)

                put(varName, VariableDeclaration(varName, bean, beanClass.starProjectedType, isMutable = false))
            }
        }

    declareBeanInstances(beanInstances)
}

private fun JupyterIntegration.Builder.declareBeanInstances(beanInstances: Map<String, VariableDeclaration>) {
    onLoaded {
        declare(beanInstances.values)
    }
}

private fun <T> Iterable<T>.forEachSafe(action: (T) -> Unit) {
    for (element in this) {
        try {
            action(element)
        } catch (_: Throwable) {
        }
    }
}
