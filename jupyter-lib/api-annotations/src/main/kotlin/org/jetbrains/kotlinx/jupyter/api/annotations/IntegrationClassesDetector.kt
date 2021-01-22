package org.jetbrains.kotlinx.jupyter.api.annotations

import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedOptions
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

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

    private val annotationClasses = mapOf(
        JupyterLibraryProducer::class to "producers",
        JupyterLibraryDefinition::class to "definitions"
    )

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return annotationClasses.keys.map { it.qualifiedName!! }.toMutableSet()
    }

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        val pathStr = processingEnv.options[KOTLIN_JUPYTER_GENERATED_PATH] ?: return false
        val path = File(pathStr)
        path.mkdirs()

        for ((annotation, fileName) in annotationClasses) {
            val annotatedElements = roundEnv.getElementsAnnotatedWith(annotation.java)
            val fqnList = mutableListOf<String>()

            for (element in annotatedElements) {
                if (element.kind != ElementKind.CLASS) throw Exception("Elements of kind ${element.kind} are not valid target for $annotation annotations")
                val classElement = element as? TypeElement ?: throw Exception("Element ${element.simpleName} is not type element")

                classElement.qualifiedName?.let { fqnList.add(it.toString()) }
            }
            val file = path.resolve(fileName)
            file.appendText(
                fqnList.joinToString("", "", "\n")
            )
        }

        return true
    }
}
