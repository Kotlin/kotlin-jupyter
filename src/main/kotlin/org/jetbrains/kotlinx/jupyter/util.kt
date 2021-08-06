package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.bufferedImageRenderer
import org.jetbrains.kotlinx.jupyter.codegen.ResultsRenderersProcessor
import org.jetbrains.kotlinx.jupyter.compiler.util.SourceCodeImpl
import org.jetbrains.kotlinx.jupyter.config.catchAll
import org.jetbrains.kotlinx.jupyter.libraries.KERNEL_LIBRARIES
import org.jetbrains.kotlinx.jupyter.libraries.parseLibraryDescriptor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.determineSep
import kotlin.script.experimental.jvm.util.toSourceCodePosition

fun List<String>.joinToLines() = joinToString("\n")

fun generateDiagnostic(fromLine: Int, fromCol: Int, toLine: Int, toCol: Int, message: String, severity: String) =
    ScriptDiagnostic(
        ScriptDiagnostic.unspecifiedError,
        message,
        ScriptDiagnostic.Severity.valueOf(severity),
        null,
        SourceCode.Location(SourceCode.Position(fromLine, fromCol), SourceCode.Position(toLine, toCol))
    )

fun generateDiagnosticFromAbsolute(code: String, from: Int, to: Int, message: String, severity: String): ScriptDiagnostic {
    val snippet = SourceCodeImpl(0, code)
    return ScriptDiagnostic(
        ScriptDiagnostic.unspecifiedError,
        message,
        ScriptDiagnostic.Severity.valueOf(severity),
        null,
        SourceCode.Location(from.toSourceCodePosition(snippet), to.toSourceCodePosition(snippet))
    )
}

fun withPath(path: String?, diagnostics: List<ScriptDiagnostic>): List<ScriptDiagnostic> =
    diagnostics.map { it.copy(sourcePath = path) }

fun String.findNthSubstring(s: String, n: Int, start: Int = 0): Int {
    if (n < 1 || start == -1) return -1

    var i = start

    for (k in 1..n) {
        i = indexOf(s, i)
        if (i == -1) return -1
        i += s.length
    }

    return i - s.length
}

fun SourceCode.Position.withNewAbsolute(code: SourceCode, newCode: SourceCode): SourceCode.Position? {
    val sep = code.text.determineSep()
    val absLineStart =
        if (line == 1) 0
        else newCode.text.findNthSubstring(sep, line - 1) + sep.length

    var nextNewLinePos = newCode.text.indexOf(sep, absLineStart)
    if (nextNewLinePos == -1) nextNewLinePos = newCode.text.length

    val abs = absLineStart + col - 1
    if (abs > nextNewLinePos) {
        return null
    }

    return SourceCode.Position(line, col, abs)
}

fun Int.toSourceCodePositionWithNewAbsolute(code: SourceCode, newCode: SourceCode): SourceCode.Position? {
    return toSourceCodePosition(code).withNewAbsolute(code, newCode)
}

fun ResultsRenderersProcessor.registerDefaultRenderers() {
    register(bufferedImageRenderer)
}

/**
 * Stores info about where a variable Y was declared and info about what are they at the address X.
 * K: key, stands for a way of addressing variables, e.g. address.
 * V: value, from Variable, choose any suitable type for your variable reference.
 * Default: K=Int, V=String
 */
class VariablesUsagesPerCellWatcher<K : Any, V : Any> {
    val cellVariables = mutableMapOf<K, MutableSet<V>>()

    /**
     * Tells in which cell a variable was declared
     */
    private val variablesDeclarationInfo: MutableMap<V, K> = mutableMapOf()

    private val unchangedVariables: MutableSet<V> = mutableSetOf()
//    private val unchangedVariables: MutableSet<V> = mutableSetOf()

    fun addDeclaration(address: K, variableRef: V) {
        ensureStorageCreation(address)

        // redeclaration of any type
        if (variablesDeclarationInfo.containsKey(variableRef)) {
            val oldCellId = variablesDeclarationInfo[variableRef]
            if (oldCellId != address) {
                cellVariables[oldCellId]?.remove(variableRef)
                unchangedVariables.remove(variableRef)
            }
        } else {
            unchangedVariables.add(variableRef)
        }
        variablesDeclarationInfo[variableRef] = address
        cellVariables[address]?.add(variableRef)
    }

    fun addUsage(address: K, variableRef: V) {
        cellVariables[address]?.add(variableRef)
        if (variablesDeclarationInfo[variableRef] != address) {
            unchangedVariables.remove(variableRef)
        }
    }

    fun removeOldUsages(newAddress: K) {
        // remove known modifying usages in this cell
        cellVariables[newAddress]?.removeIf {
            val predicate = variablesDeclarationInfo[it] != newAddress
            if (predicate) {
                unchangedVariables.add(it)
            }
            predicate
        }
    }

    fun getUnchangedVariables(): Set<V> = unchangedVariables

    fun findDeclarationAddress(variableRef: V) = variablesDeclarationInfo[variableRef]

    fun ensureStorageCreation(address: K) = cellVariables.putIfAbsent(address, mutableSetOf())
}

fun <A, V> createCachedFun(calculate: (A) -> V): (A) -> V {
    return createCachedFun({ it }, calculate)
}

fun <A, K, V> createCachedFun(calculateKey: (A) -> K, calculate: (A) -> V): (A) -> V {
    val cache = ConcurrentHashMap<K, V>()
    return { argument ->
        val key = calculateKey(argument)
        cache.getOrPut(key) {
            calculate(argument)
        }
    }
}

val libraryDescriptors = createCachedFun(calculateKey = { file: File -> file.absolutePath }) { homeDir ->
    val libraryFiles = KERNEL_LIBRARIES
        .homeLibrariesDir(homeDir)
        .listFiles(KERNEL_LIBRARIES::isLibraryDescriptor)
        .orEmpty()
    libraryFiles.toList().mapNotNull { file ->
        val libraryName = file.nameWithoutExtension
        log.info("Parsing descriptor for library '$libraryName'")
        log.catchAll(msg = "Parsing descriptor for library '$libraryName' failed") {
            libraryName to parseLibraryDescriptor(file.readText())
        }
    }.toMap()
}
