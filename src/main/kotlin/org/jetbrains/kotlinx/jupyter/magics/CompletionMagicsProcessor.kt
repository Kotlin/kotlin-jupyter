package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.createCachedFun
import org.jetbrains.kotlinx.jupyter.defaultRepositories
import org.jetbrains.kotlinx.jupyter.libraries.libraryCommaRanges
import org.jetbrains.kotlinx.jupyter.libraryDescriptors
import org.jetbrains.kotlinx.jupyter.log
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.script.experimental.api.SourceCodeCompletionVariant

class CompletionMagicsProcessor(
    private val homeDir: File?,
) : AbstractMagicsProcessor() {

    fun process(code: String, cursor: Int): Result {
        val magics = magicsIntervals(code)
        var insideMagic = false
        val handler = Handler()

        for (magicRange in magics) {
            if (cursor in (magicRange.from + 1)..magicRange.to) {
                insideMagic = true
                if (code[magicRange.from] != MAGICS_SIGN || cursor == magicRange.from) continue

                val magicText = code.substring(magicRange.from + 1, magicRange.to).trim()
                log.catchAll(msg = "Handling completion of $magicText failed") {
                    handler.handle(magicText, cursor - magicRange.from - 1)
                }
            }
        }

        val codes = codeIntervals(code, magics, true)
        val preprocessedCode = codes.joinToString("") { code.substring(it.from, it.to) }
        return Result(preprocessedCode, insideMagic, handler.completions)
    }

    class Result(
        val code: String,
        val cursorInsideMagic: Boolean,
        val completions: List<SourceCodeCompletionVariant>
    )

    private inner class Handler {
        private val _completions = mutableListOf<SourceCodeCompletionVariant>()
        val completions: List<SourceCodeCompletionVariant> get() = _completions.distinctBy { it.text }

        fun handle(magicText: String, cursor: Int) {
            val firstSpaceIndex = magicText.indexOf(' ')
            if (cursor <= firstSpaceIndex || firstSpaceIndex == -1) {
                val magicPrefix = magicText.substring(0, cursor)
                val suggestions = ReplLineMagic.codeInsightValues.filter { it.name.startsWith(magicPrefix) }
                suggestions.mapTo(_completions) { mg ->
                    SourceCodeCompletionVariant(mg.name, mg.name, mg.type.name, mg.type.name)
                }
            } else {
                val magicName = magicText.substring(0, firstSpaceIndex)
                val argument = magicText.substring(firstSpaceIndex)
                val cursorToArgument = cursor - firstSpaceIndex
                when (ReplLineMagic.valueOfOrNull(magicName)?.value) {
                    ReplLineMagic.USE -> {
                        for ((from, to) in libraryCommaRanges(argument)) {
                            if (cursorToArgument in (from + 1)..to) {
                                val libArgPart = argument.substring(from + 1, cursorToArgument)
                                handleLibrary(libArgPart, cursorToArgument - from - 1)
                                break
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        private fun handleLibrary(librarySubstring: String, cursor: Int) {
            if (homeDir == null) return
            val descriptors = libraryDescriptors(homeDir)

            val firstBracketIndex = librarySubstring.indexOf('(')
            if (cursor <= firstBracketIndex || firstBracketIndex == -1) {
                val libNamePrefix = librarySubstring.substring(0, cursor).trimStart()
                val sufficientNames = descriptors.keys.filter { it.startsWith(libNamePrefix) }
                sufficientNames.mapTo(_completions) {
                    SourceCodeCompletionVariant(it, it, "library", "library")
                }
            } else {
                val versionArgPrefix = librarySubstring.substring(firstBracketIndex + 1).trimStart()
                if (versionArgPrefix.indexOfAny(charArrayOf('(', ',', '@', '=')) != -1) return
                val libName = librarySubstring.substring(0, firstBracketIndex).trim()
                val descriptor = descriptors.filter { (name, _) -> name.startsWith(libName) }.values.singleOrNull() ?: return
                val paramName = descriptor.variables.singleOrNull()?.name ?: return
                for (dependencyStr in descriptor.dependencies) {
                    val match = MAVEN_DEP_REGEX.matchEntire(dependencyStr) ?: continue
                    val group = match.groups[1]!!.value
                    val artifact = match.groups[2]!!.value
                    val versionTemplate = match.groups[3]!!.value
                    if (versionTemplate != "\$$paramName") continue
                    val versions = (descriptor.repositories + defaultRepositories.map { it.string }).firstNotNullOfOrNull { repo ->
                        getVersions(ArtifactLocation(repo, group, artifact))
                    }.orEmpty()
                    val matchingVersions = versions.filter { it.startsWith(versionArgPrefix) }.reversed()
                    matchingVersions.mapTo(_completions) {
                        SourceCodeCompletionVariant(it, it, "version", "version")
                    }
                }
            }
        }
    }

    companion object {
        private val MAVEN_DEP_REGEX = "^([^:]+):([^:]+):([^:]+)$".toRegex()

        private data class ArtifactLocation(val repository: String, val group: String, val artifact: String)

        private fun metadataUrl(artifactLocation: ArtifactLocation): String {
            val repo = with(artifactLocation.repository) { if (endsWith('/')) this else "$this/" }
            return "$repo${artifactLocation.group.replace(".", "/")}/${artifactLocation.artifact}/maven-metadata.xml"
        }

        private val getVersions = createCachedFun { artifactLocation: ArtifactLocation ->
            val metadataXml = getHttp(metadataUrl(artifactLocation)).takeIf { it.status.successful } ?: return@createCachedFun null
            val document = loadXML(metadataXml.text)
            val versionsTag = document
                .getElementsByTagName("versions")
                .takeIf { it.length == 1 }
                ?.item(0) ?: return@createCachedFun emptyList()

            (versionsTag as? Element)?.getElementsByTagName("version")
                ?.toList()
                ?.map { it.textContent }
                .orEmpty()
        }

        private fun loadXML(xml: String): Document {
            val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
            val builder: DocumentBuilder = factory.newDocumentBuilder()
            val inputSource = InputSource(StringReader(xml))
            return builder.parse(inputSource)
        }

        private fun NodeList.toList(): List<Node> {
            return buildList {
                for (i in 0 until length) {
                    add(item(i))
                }
            }
        }
    }
}
