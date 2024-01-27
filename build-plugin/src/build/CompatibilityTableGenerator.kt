package build

import build.util.INTERNAL_TEAMCITY_URL
import build.util.TeamcityProject
import kotlinx.serialization.json.*
import org.gradle.api.Project
import org.gradle.api.Task
import org.jetbrains.kotlinx.jupyter.api.KotlinKernelVersion
import java.io.File
import kotlin.math.max
import org.jetbrains.kotlinx.jupyter.common.httpRequest
import org.jetbrains.kotlinx.jupyter.common.jsonObject
import org.jetbrains.kotlinx.jupyter.common.Request
import org.jetbrains.kotlinx.jupyter.common.successful
import java.util.TreeMap

class CompatibilityTableGenerator(
    private val project: Project,
    private val settings: RootSettingsExtension,
) {
    private val kernelTeamcity = TeamcityProject(INTERNAL_TEAMCITY_URL, "KDS_KotlinJupyter_Conda")
    private val unknownInfo = "?"

    private val mdHeaderToTcProp = HashMap<String, String>().apply {
        for (attr in settings.compatibilityAttributes) {
            put(attr.mdDescription, attr.tcPropertyName)
        }
    }

    fun registerTasks(configuration: Task.() -> Unit) {
        val mdFile = project.rootDir.resolve(settings.compatibilityTableFileName)
        project.tasks.register(GENERATE_COMPAT_TABLE) {
            group = HELP_GROUP

            inputs.file(mdFile)
            outputs.file(mdFile)
            outputs.upToDateWhen { false }

            doLast {
                updateMdFile(mdFile)
            }

            configuration()
        }
    }

    private fun updateMdFile(mdFile: File) {
        val mdLines = mdFile.readLines()
        val headerSeparatorIndex = mdLines.indexOfFirst { it.startsWith("|:-") }
        val dataLinesIndices = mutableListOf<Int>().apply {
            for (i in (headerSeparatorIndex + 1) until mdLines.size) {
                val line = mdLines[i]
                if (line.startsWith("|")) {
                    add(i)
                }
            }
        }

        val widths = mdLines[headerSeparatorIndex]
                .split('|')
                .filter { it.isNotBlank() }
                .map { it.length }

        val columns = mdLines[headerSeparatorIndex - 1]
                .split('|')
                .filter { it.isNotBlank() }
                .map { mdHeaderToTcProp[it.trim()] }

        val existingVersions = HashMap<String, Pair<Int, List<String> > >().apply {
            for (i in dataLinesIndices) {
                val line = mdLines[i]
                val versionLine = line.split('|').filter { it.isNotBlank() }.map { it.trim() }
                put(versionLine[0], i to versionLine)
            }
        }

        val newVersions = TreeMap<KotlinKernelVersion, List<String>>().apply {
            val allBuildsUrl = "${kernelTeamcity.url}/guestAuth/app/rest/builds/multiple/status:success,buildType:(id:${kernelTeamcity.projectId})"
            val allBuildsResponse = httpRequest(
                    Request("GET", allBuildsUrl)
                            .header("Accept", "application/json")
            )
            val allBuildsJson = allBuildsResponse.jsonObject
            val allBuilds = allBuildsJson["build"]!!.jsonArray
            val buildIds = allBuilds.map { buildObject ->
                buildObject.jsonObject["id"]!!.jsonPrimitive.int
            }
            for (buildId in buildIds) {
                val artifactUrl = "${kernelTeamcity.url}/guestAuth/app/rest/builds/id:${buildId}/artifacts/content/teamcity-artifacts/${settings.versionsCompatFileName}"
                val artifactResponse = httpRequest(
                        Request("GET", artifactUrl)
                )
                if(artifactResponse.status.successful) {
                    val verMap = artifactResponse.text
                            .lines()
                            .filter { it.contains("=") }
                            .associate { it.substringBefore("=") to it.substringAfter("=") }
                    val verList = columns.map { tcProp ->
                        verMap[tcProp] ?: unknownInfo
                    }
                    val pyVersion = verList[0]
                    put(KotlinKernelVersion.from(pyVersion) ?: error("Can't parse version $pyVersion"), verList)
                }
            }
        }

        var newLineNumCnt = (dataLinesIndices.lastOrNull() ?: headerSeparatorIndex) + 1

        for ((versionKey, row) in newVersions) {
            val key = versionKey.toString()
            if (key in existingVersions) {
                // update existing
                val (index, oldRow) = existingVersions[key]!!
                val newRow = row.indices.map { i ->
                    if (row[i] == unknownInfo) oldRow[i] else row[i]
                }
                existingVersions[key] = index to newRow
            } else {
                // add new
                existingVersions[key] = newLineNumCnt to row
                ++newLineNumCnt
            }
        }

        val linesToUpdate = existingVersions.values.toMap()
        val newLinesCount = max(linesToUpdate.keys.maxOrNull() ?: 0, mdLines.lastIndex) + 2

        val newLines = mutableListOf<String>().apply {
            for (i in 0 until newLinesCount) {
                if (i in linesToUpdate) {
                    var j = -1
                    add(linesToUpdate[i]!!.joinToString("|", "|", "|") {
                        ++j
                        val width = widths[j]
                        val spacesCount = width - it.length
                        if (spacesCount <= 0) {
                            it
                        } else {
                            " " + it + " ".repeat(spacesCount - 1)
                        }
                    })
                } else {
                    add(mdLines.getOrNull(i).orEmpty())
                }
            }
        }

        mdFile.writeText(newLines.joinToString("\n"))
    }
}