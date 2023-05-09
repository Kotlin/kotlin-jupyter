package org.jetbrains.kotlinx.jupyter.magics

import org.jetbrains.kotlinx.jupyter.common.ReplLineMagic
import org.jetbrains.kotlinx.jupyter.config.defaultRepositories
import org.jetbrains.kotlinx.jupyter.libraries.Brackets
import org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptorsProvider
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryArguments
import org.jetbrains.kotlinx.jupyter.util.createCachedFun
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

abstract class AbstractCompletionMagicsProcessor<V : Any>(
    private val libraryDescriptorsProvider: LibraryDescriptorsProvider,
    parseOutCellMarker: Boolean = false,
) : AbstractMagicsProcessor(parseOutCellMarker) {

    protected abstract fun variant(text: String, icon: String): V
    protected abstract fun key(variant: V): String
    protected abstract fun getHttpResponseText(url: String): String?

    private val getVersions = createCachedFun { artifactLocation: ArtifactLocation ->
        val responseText = getHttpResponseText(metadataUrl(artifactLocation)) ?: return@createCachedFun null
        val document = loadXML(responseText)
        val versionsTag = document
            .getElementsByTagName("versions")
            .singleOrNull() ?: return@createCachedFun emptyList()

        (versionsTag as? Element)?.getElementsByTagName("version")
            ?.toList()
            ?.map { it.textContent }
            .orEmpty()
    }

    protected inner class Handler {
        private val _completions = mutableListOf<V>()
        val completions: List<V> get() = _completions.distinctBy { key(it) }

        fun handle(magicText: String, cursor: Int) {
            val firstSpaceIndex = magicText.indexOf(' ')
            if (cursor <= firstSpaceIndex || firstSpaceIndex == -1) {
                val magicPrefix = magicText.substring(0, cursor)
                val suggestions = ReplLineMagic.codeInsightValues.filter { it.name.startsWith(magicPrefix) }
                suggestions.mapTo(_completions) { mg ->
                    variant(mg.name, mg.type.name)
                }
            } else {
                val magicName = magicText.substring(0, firstSpaceIndex)
                val argument = magicText.substring(firstSpaceIndex)
                val cursorToArgument = cursor - firstSpaceIndex
                when (ReplLineMagic.valueOfOrNull(magicName)?.value) {
                    ReplLineMagic.USE -> {
                        for ((from, to) in libraryCommaRanges(argument)) {
                            if (cursorToArgument in (from + 1)..to) {
                                val libArgPart = argument.substring(from + 1, to)
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
            val descriptors = libraryDescriptorsProvider.getDescriptors()

            val firstBracketIndex = librarySubstring.indexOf('(')
            if (cursor <= firstBracketIndex || firstBracketIndex == -1) {
                val libNamePrefix = librarySubstring.substring(0, cursor).trimStart()
                val sufficientNames = descriptors.keys.filter { it.startsWith(libNamePrefix) }
                sufficientNames.mapTo(_completions) {
                    variant(it, "library")
                }
            } else {
                val callArgs = parseLibraryArguments("$librarySubstring)", Brackets.ROUND, firstBracketIndex + 1).toList()
                if (callArgs.isEmpty()) return

                val argIndex = callArgs.indexOfFirst { cursor < it.end }
                if (argIndex == -1) return

                val argCallStart = if (argIndex == 0) firstBracketIndex + 1 else callArgs[argIndex - 1].end
                val argCall = librarySubstring.substring(argCallStart, cursor)
                val argName = callArgs[argIndex].variable.name
                val libName = librarySubstring.substring(0, firstBracketIndex).trim()
                val libraryDescriptor = libraryDescriptorsProvider.getDescriptorForVersionsCompletion(libName) ?: return
                val globalOptions = libraryDescriptorsProvider.getDescriptorGlobalOptions()
                val parameters = libraryDescriptor.variables
                val paramNames = parameters.properties
                    .map { it.name }
                    .filter { !globalOptions.isPropertyIgnored(it) }
                if (paramNames.isEmpty()) return

                if ('=' !in argCall) {
                    paramNames.filter { it.startsWith(argCall) }.mapTo(_completions) {
                        variant(it, "parameter")
                    }
                    if (argName.isNotEmpty()) return
                }

                val argValuePrefix = argCall.substringAfter('=').trimStart()

                val paramsHaveOrder = parameters.hasOrder
                val paramName = argName.ifEmpty {
                    if (paramsHaveOrder) {
                        paramNames.getOrNull(argIndex)
                    } else {
                        paramNames.singleOrNull()
                    } ?: return
                }

                for (dependencyStr in libraryDescriptor.dependencies) {
                    val match = MAVEN_DEP_REGEX.matchEntire(dependencyStr) ?: continue
                    val group = match.groups[1]!!.value
                    val artifact = match.groups[2]!!.value

                    val versionTemplate = match.groups[3]!!.value
                    if (!versionTemplate.startsWith("$")) continue
                    val dependencyParamName = versionTemplate.substring(1)
                    if (dependencyParamName != paramName) continue

                    val versions = (libraryDescriptor.repositories + defaultRepositories).firstNotNullOfOrNull { repo ->
                        if (repo.username == null && repo.password == null) {
                            getVersions(ArtifactLocation(repo.path, group, artifact))
                        } else {
                            null
                        }
                    }.orEmpty()
                    val matchingVersions = versions.filter { it.startsWith(argValuePrefix) }.reversed()
                    matchingVersions.mapTo(_completions) {
                        variant(it, "version")
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

        private fun loadXML(xml: String): Document {
            val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
            val builder: DocumentBuilder = factory.newDocumentBuilder()
            val inputSource = InputSource(StringReader(xml))
            return builder.parse(inputSource)
        }

        private fun NodeList.toList(): List<Node> {
            return object : AbstractList<Node>() {
                override val size: Int get() = length
                override fun get(index: Int) = item(index)
            }
        }

        private fun NodeList.singleOrNull() = toList().singleOrNull()
    }
}
