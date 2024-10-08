package org.jetbrains.kotlinx.jupyter.spring.starter

import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import jupyter.kotlin.USE
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType

@Suppress("unused")
fun ScriptTemplateWithDisplayHelpers.declareAllBeans() {
    declareBeansByNames(springContext.beanDefinitionNames.toList())
}

@Suppress("unused")
fun ScriptTemplateWithDisplayHelpers.declareBeansByClasses(beanClasses: Iterable<KClass<*>>) {
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

fun ScriptTemplateWithDisplayHelpers.declareBeansByNames(beanNames: Iterable<String>) {
    val beanInstances =
        buildMap {
            beanNames.forEachSafe { beanName ->
                val varName = beanName.substringAfterLast(".")
                if (varName.contains("$")) return@forEachSafe

                val beanClass = springContext.getType(beanName).kotlin

                val qualifiedName = beanClass.qualifiedName ?: return@forEachSafe
                if (qualifiedName.contains("$") || qualifiedName.startsWith("com.sun.")) return@forEachSafe
                if (beanClass.visibility != KVisibility.PUBLIC) return@forEachSafe

                val bean = springContext.getBean(beanClass.java)

                put(varName, VariableDeclaration(varName, bean, beanClass.starProjectedType, isMutable = false))
            }
        }

    declareBeanInstances(beanInstances)
}

private fun ScriptTemplateWithDisplayHelpers.declareBeanInstances(beanInstances: Map<String, VariableDeclaration>) {
    USE {
        onLoaded {
            declare(beanInstances.values)
        }
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
