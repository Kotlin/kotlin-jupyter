package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.common.text
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.createCachedFun
import org.jetbrains.kotlinx.jupyter.libraries.libraryCommaRanges
import org.jetbrains.kotlinx.jupyter.libraryDescriptors
import org.jetbrains.kotlinx.jupyter.log
import org.w3c.dom.Document
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
        val completions: List<SourceCodeCompletionVariant> get() = _completions

        fun handle(magicText: String, cursor: Int) {
            val firstSpaceIndex = magicText.indexOf(' ')
            if (cursor <= firstSpaceIndex || firstSpaceIndex == -1) {
                val magicPrefix = magicText.substring(0, cursor)
                val suggestions = ReplLineMagic.codeInsightValues.filter { it.name.startsWith(magicPrefix) }
                _completions.addAll(
                    suggestions.map { mg ->
                        SourceCodeCompletionVariant(mg.name, mg.name, mg.type.name, mg.type.name)
                    }
                )
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
                for (dependencyStr in descriptor.dependencies) {
                    val match = MAVEN_DEP_REGEX.matchEntire(dependencyStr) ?: continue
                    val group = match.groups[1]!!.value
                    val artifact = match.groups[2]!!.value
                    val versions = getVersions(GA(group, artifact))
                    val matchingVersions = versions.filter { it.startsWith(versionArgPrefix) }
                    matchingVersions.mapTo(_completions) {
                        SourceCodeCompletionVariant(it, it, "version", "version")
                    }
                }
            }
        }
    }

    companion object {
        private val MAVEN_DEP_REGEX = "^([^:]+):([^:]+):([^:]+)$".toRegex()

        data class GA(val group: String, val artifact: String)

        private fun metadataUrl(ga: GA): String {
            return "https://repo1.maven.org/maven2/${ga.group.replace(".", "/")}/${ga.artifact}/maven-metadata.xml"
        }

        private val getVersions = createCachedFun { ga: GA ->
            val metadataXml = getHttp(metadataUrl(ga)).takeIf { it.status.successful } ?: return@createCachedFun emptyList()
            val document = loadXML(metadataXml.text)
            val versionsTag = document
                .getElementsByTagName("versions")
                .takeIf { it.length == 1 }
                ?.item(0) ?: return@createCachedFun emptyList()

            versionsTag.childNodes.toList().map { it.textContent }
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
