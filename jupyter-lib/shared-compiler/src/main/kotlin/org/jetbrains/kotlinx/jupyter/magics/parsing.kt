package org.jetbrains.kotlinx.jupyter.magics

/**
 * Split a command argument into a list of library calls
 */
fun splitLibraryCalls(text: String): List<String> {
    return libraryCommaRanges(text)
        .mapNotNull { (from, to) ->
            if (from >= to) null
            else text.substring(from + 1, to).trim().takeIf { it.isNotEmpty() }
        }
}

fun libraryCommaRanges(text: String): List<Pair<Int, Int>> {
    return libraryCommaIndices(text, withFirst = true, withLast = true).zipWithNext()
}

/**
 * Need special processing of ',' to skip call argument delimiters in brackets
 * E.g. "use lib1(3), lib2(2, 5)" should split into "lib1(3)" and "lib(2, 5)", not into "lib1(3)", "lib(2", "5)"
 */
fun libraryCommaIndices(text: String, withFirst: Boolean = false, withLast: Boolean = false): List<Int> {
    return buildList {
        var i = 0
        var commaDepth = 0
        val delimiters = charArrayOf(',', '(', ')')
        if (withFirst) add(-1)

        while (true) {
            i = text.indexOfAny(delimiters, i)
            if (i == -1) {
                if (withLast) add(text.length)
                break
            }
            when (text[i]) {
                ',' -> if (commaDepth == 0) {
                    add(i)
                }
                '(' -> commaDepth++
                ')' -> commaDepth--
            }
            i++
        }
    }
}
