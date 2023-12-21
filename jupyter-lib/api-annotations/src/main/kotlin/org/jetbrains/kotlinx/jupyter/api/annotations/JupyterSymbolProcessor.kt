package org.jetbrains.kotlinx.jupyter.api.annotations

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.File
import java.util.Spliterators
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.concurrent.withLock

class JupyterSymbolProcessor(
    private val logger: KSPLogger,
    private val generatedFilesPath: File,
) : SymbolProcessor {
    private val fqnMap = mapOf(
        "org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer" to "producers",
        "org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition" to "definitions",
    )

    private val annotationFqn = JupyterLibrary::class.qualifiedName!!
    private val annotationSimpleName = JupyterLibrary::class.simpleName!!

    private val fileLock = ReentrantLock()
    private val resolveLock = ReentrantLock()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        generatedFilesPath.deleteRecursively()
        generatedFilesPath.mkdirs()

        resolver
            .getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .asParallelStream()
            .forEach(::processClass)

        return emptyList()
    }

    private fun processClass(clazz: KSClassDeclaration) {
        if (!hasLibraryAnnotation(clazz)) return
        val classFqn = clazz.qualifiedName?.asString()
            ?: throw Exception("Class $clazz was marked with $annotationSimpleName, but it has no qualified name (anonymous?).")

        logger.info("Class $classFqn has $annotationSimpleName annotation")

        val supertypes = clazz.getAllSuperTypes().mapNotNull { it.declaration.qualifiedName }.map { it.asString() }
        val significantSupertypes = supertypes.filter { it in fqnMap }.toList()

        if (significantSupertypes.isEmpty()) {
            logger.warn(
                "Class $classFqn has $annotationSimpleName annotation, " +
                    "but doesn't implement one of Jupyter integration interfaces",
            )
            return
        }

        fileLock.withLock {
            for (fqn in significantSupertypes) {
                val fileName = fqnMap[fqn]!!
                val file = generatedFilesPath.resolve(fileName)
                file.appendText(classFqn + "\n")
            }
        }
    }

    private fun hasLibraryAnnotation(clazz: KSClassDeclaration): Boolean {
        return clazz.annotations.any {
            val type = resolveLock.withLock { it.annotationType.resolve() }
            type.declaration.qualifiedName?.asString() == annotationFqn
        }
    }

    private fun <T> Sequence<T>.asParallelStream(): Stream<T> {
        return StreamSupport.stream({ Spliterators.spliteratorUnknownSize(iterator(), 0) }, 0, true)
    }
}
