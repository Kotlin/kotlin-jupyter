package org.jetbrains.kotlinx.jupyter.spring.starter

import jupyter.kotlin.ScriptTemplateWithDisplayHelpers
import org.jetbrains.kotlinx.jupyter.api.VariableDeclaration
import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.superclasses

@Suppress("unused")
fun ScriptTemplateWithDisplayHelpers.declareAllBeans() {
    USE {
        declareAllBeansInLibrary()
    }
}

fun JupyterIntegration.Builder.declareAllBeansInLibrary() {
    if (SpringContext.getContext() == null) {
        println("Spring context is not initialized, variables for beans won't be declared.")
        return
    }
    val beanNames =
        buildSet {
            addAll(springContext.beanDefinitionNames)
            addAll(springContext.getBeansOfType(Any::class.java).keys)
        }

    declareBeansByNames(beanNames)
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
                if (varName in forbiddenVariableNames) return@forEachSafe

                val beanClass =
                    springContext
                        .getType(beanName)
                        ?.kotlin
                        ?.findSuitableBeanClass() ?: return@forEachSafe

                val bean =
                    runCatching {
                        springContext.getBean(beanClass.java)
                    }.getOrNull() ?: return@forEachSafe

                put(varName, VariableDeclaration(varName, bean, beanClass.starProjectedType, isMutable = false))
            }
        }

    declareBeanInstances(beanInstances)
}

private val forbiddenVariableNames =
    setOf(
        "springContext",
        "notebook",
    )

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

private fun KClass<*>.findSuitableBeanClass(): KClass<*>? {
    val classesToCheck =
        generateSequence(this) { clazz ->
            clazz.superclasses.firstOrNull { superclass ->
                !superclass.java.isInterface
            }
        }

    return classesToCheck.firstOrNull { clazz ->
        if (clazz.visibility != KVisibility.PUBLIC || clazz == Any::class) return@firstOrNull false
        val name = clazz.qualifiedName ?: return@firstOrNull false
        "$" !in name && !name.startsWith("com.sun.")
    }
}
