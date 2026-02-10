package build.util

import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonArray
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.file.FileTreeElement

/**
 * A transformer that merges multiple kotlin-jupyter-libraries/libraries.json files.
 * It merges the "definitions", "producers", and "descriptors" arrays from all encountered files.
 */
class LibrariesJsonMergeTransformer : ResourceTransformer {
    private val path: String = "META-INF/kotlin-jupyter-libraries/libraries.json"
    private val elements =
        listOf(
            "definitions",
            "producers",
            "descriptors",
        ).associateWith { mutableListOf<JsonElement>() }

    override fun getName(): String = "Libraries JSON Merge Transformer for $path"

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.relativePath.pathString == path
    }

    override fun transform(context: TransformerContext) {
        if (context.path != path) return

        val content = context.inputStream.reader().use { it.readText() }
        val json = Json.parseToJsonElement(content).jsonObject

        for ((key, list) in elements) {
            val keyElements = json[key]?.jsonArray ?: continue
            list.addAll(keyElements)
        }
    }

    override fun hasTransformedResource(): Boolean = elements.any { it.value.isNotEmpty() }

    override fun modifyOutputStream(
        os: ZipOutputStream,
        preserveFileTimestamps: Boolean,
    ) {
        if (!hasTransformedResource()) return

        val mergedJson =
            buildJsonObject {
                for ((key, list) in elements) {
                    putJsonArray(key) {
                        for (element in list.distinct()) {
                            add(element)
                        }
                    }
                }
            }

        val entry = ZipEntry(path)
        os.putNextEntry(entry)
        os.write(mergedJson.toString().toByteArray())
    }
}
