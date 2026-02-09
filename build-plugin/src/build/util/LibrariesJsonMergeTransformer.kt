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
    private val definitions = mutableListOf<JsonElement>()
    private val producers = mutableListOf<JsonElement>()
    private val descriptors = mutableListOf<JsonElement>()

    override fun getName(): String = "Libraries JSON Merge Transformer for $path"

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.relativePath.pathString == path
    }

    override fun transform(context: TransformerContext) {
        if (context.path != path) return

        val content = context.inputStream.reader().use { it.readText() }
        val json = Json.parseToJsonElement(content).jsonObject

        json["definitions"]?.jsonArray?.let { definitions.addAll(it) }
        json["producers"]?.jsonArray?.let { producers.addAll(it) }
        json["descriptors"]?.jsonArray?.let { descriptors.addAll(it) }
    }

    override fun hasTransformedResource(): Boolean = definitions.isNotEmpty() || producers.isNotEmpty() || descriptors.isNotEmpty()

    override fun modifyOutputStream(
        os: ZipOutputStream,
        preserveFileTimestamps: Boolean,
    ) {
        if (!hasTransformedResource()) return

        val mergedJson =
            buildJsonObject {
                putJsonArray("definitions") { definitions.distinct().forEach { add(it) } }
                putJsonArray("producers") { producers.distinct().forEach { add(it) } }
                putJsonArray("descriptors") { descriptors.distinct().forEach { add(it) } }
            }

        val entry = ZipEntry(path)
        os.putNextEntry(entry)
        os.write(mergedJson.toString().toByteArray())
    }
}
