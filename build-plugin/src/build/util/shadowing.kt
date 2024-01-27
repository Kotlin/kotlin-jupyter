package build.util

import com.github.jengelman.gradle.plugins.shadow.ShadowStats
import com.github.jengelman.gradle.plugins.shadow.relocation.RelocateClassContext
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.gradle.api.file.FileTreeElement
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import shadow.org.apache.tools.zip.ZipEntry
import shadow.org.apache.tools.zip.ZipOutputStream
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class RelocateDsl {
    val includes = mutableSetOf<String>()
    val excludes = mutableSetOf<String>()
    var prefix: String = "ktnb."

    operator fun String.unaryPlus() = includes.add(this)
    operator fun String.unaryMinus() = excludes.add(this)
}

fun ShadowJar.relocatePackages(configure: RelocateDsl.() -> Unit) {
    val configurator = RelocateDsl()
    configurator.configure()

    for (include in configurator.includes) {
        relocate(include, configurator.prefix + include) {
            for (exclude in configurator.excludes) {
                this.exclude(exclude)
            }
        }
    }
}

class ContentModificationContext(
    val content: String,
    val relocators: List<Relocator>,
    val shadowStats: ShadowStats,
)

class ContentTransformer(
    private val path: String,
    private val contentModifier: ContentModificationContext.() -> String = { content },
) : Transformer {
    private var modifiedContent: String? = null

    override fun transform(context: TransformerContext?) {
        if (context == null) return
        val path = context.path

        if (path == this.path) {
            val content = context.`is`.reader().use { it.readText() }
            val modificationContext = ContentModificationContext(content, context.relocators, context.stats)
            modifiedContent = modificationContext.contentModifier()
        }
    }

    override fun getName(): String {
        return "Content Transformer for file $path"
    }

    override fun canTransformResource(element: FileTreeElement): Boolean {
        return element.relativePath.pathString == path
    }

    override fun modifyOutputStream(zipOutputStream: ZipOutputStream?, preserveFileTimestamps: Boolean) {
        val content = modifiedContent ?: return
        val os = zipOutputStream ?: return

        val entry = ZipEntry(path)
        entry.time = TransformerContext.getEntryTimestamp(preserveFileTimestamps, entry.time)
        os.putNextEntry(entry)
        os.write(content.toByteArray())
    }

    override fun hasTransformedResource(): Boolean = modifiedContent != null
}

fun ContentModificationContext.transformPluginXmlContent(): String {
    val dbFactory = DocumentBuilderFactory.newInstance()
    val dBuilder = dbFactory.newDocumentBuilder()
    val stream = ByteArrayInputStream(content.toByteArray(StandardCharsets.UTF_8))
    val doc: Document = dBuilder.parse(stream)
    val ideaPlugin = doc.documentElement

    fun relocateClass(fqn: String): String {
        val relocator = relocators.firstOrNull { it.canRelocateClass(fqn) } ?: return fqn
        return relocator.relocateClass(RelocateClassContext(fqn, shadowStats))
    }

    val extensionPointLists = ideaPlugin.getElementsByTagName("extensionPoints")
        .toElements()
    for (extensionPointsNode in extensionPointLists) {
        val extensionPoints = extensionPointsNode.getElementsByTagName("extensionPoint")
            .toElements()

        for (extensionPoint in extensionPoints) {
            extensionPoint.transformAttributeValue("qualifiedName", ::relocateClass)
            extensionPoint.transformAttributeValue("interface", ::relocateClass)
        }
    }

    return doc.toText()
}

fun Document.toText(): String {
    val tf = TransformerFactory.newInstance()
    val transformer = tf.newTransformer()
    val writer = StringWriter()
    transformer.transform(DOMSource(this), StreamResult(writer))
    return writer.buffer.toString()
}

fun NodeList.toElements() = toList().filterIsInstance<Element>()

fun NodeList.toList(): List<Node> {
    return (0 until length).map { item(it) }
}

fun Element.transformAttributeValue(attributeName: String, transform: (String) -> String) {
    setAttribute(attributeName, transform(getAttribute(attributeName)))
}
