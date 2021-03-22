package org.jetbrains.kotlinx.jupyter.api.annotations

import java.io.File
import java.util.LinkedList
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(
    IntegrationClassesDetector.KAPT_KOTLIN_GENERATED_OPTION_NAME,
    IntegrationClassesDetector.KOTLIN_JUPYTER_GENERATED_PATH
)
class IntegrationClassesDetector : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val KOTLIN_JUPYTER_GENERATED_PATH = "kotlin.jupyter.fqn.path"
    }

    private val annotationClass = JupyterLibrary::class

    private val fqnMap = mapOf(
        "org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer" to "producers",
        "org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition" to "definitions"
    )

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(annotationClass.qualifiedName!!)
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val pathStr = processingEnv.options[KOTLIN_JUPYTER_GENERATED_PATH] ?: return false
        val path = File(pathStr)
        path.mkdirs()

        val annotatedElements = roundEnv.getElementsAnnotatedWith(annotationClass.java)

        for (element in annotatedElements) {
            if (element.kind != ElementKind.CLASS) throw Exception("Elements of kind ${element.kind} are not valid target for $annotationClass annotations")
            element as? TypeElement ?: throw Exception("Element ${element.simpleName} is not type element")

            val supertypes = allSupertypes(element)
            val entry = fqnMap.entries.firstOrNull { it.key in supertypes } ?: throw Exception("Type of ${element.simpleName} is not acceptable")
            val fileName = entry.value

            val fqn = element.qualifiedName?.toString() ?: continue
            val file = path.resolve(fileName)
            file.appendText(fqn + "\n")
        }

        return true
    }

    private fun allSupertypes(type: TypeElement): Set<String> {
        val utils = processingEnv.typeUtils
        val supertypes = mutableSetOf<String>()
        val q = LinkedList<TypeMirror>()

        type.qualifiedName?.let {
            supertypes.add(it.toString())
            q.add(type.asType())
        }

        while (q.isNotEmpty()) {
            val currentType = q.remove()
            val currentSupertypes = utils.directSupertypes(currentType)
            for (node in currentSupertypes) {
                val nodeElement = utils.asElement(node) as? TypeElement ?: continue
                val nodeName = nodeElement.qualifiedName.toString()
                if (nodeName in supertypes) continue
                supertypes.add(nodeName)
                q.add(node)
            }
        }

        return supertypes
    }
}
