/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.jupyter.repl.completion

import com.beust.klaxon.JsonObject
import org.jetbrains.kotlin.jupyter.jsonObject
import org.jetbrains.kotlin.jupyter.repl.context.KotlinContext
import org.jetbrains.kotlin.jupyter.repl.reflect.KotlinReflectUtil.shorten
import java.util.TreeMap
import java.io.PrintWriter
import java.io.StringWriter

enum class CompletionStatus(private val value: String) {
    OK("ok"),
    ERROR("error");

    override fun toString(): String {
        return value
    }
}

abstract class CompletionResult(
        val status: CompletionStatus
) {
    open fun toJson(): JsonObject {
        return jsonObject("status" to status.toString())
    }
}

data class CompletionTokenBounds(val start: Int, val end: Int)

class CompletionResultSuccess(
        val matches: List<String>,
        val bounds: CompletionTokenBounds,
        val metadata: Map<String, String>
): CompletionResult(CompletionStatus.OK) {
    override fun toJson(): JsonObject {
        val res = super.toJson()
        res["matches"] = matches
        res["cursor_start"] = bounds.start
        res["cursor_end"] = bounds.end
        res["metadata"] = metadata
        return res
    }
}

class CompletionResultError(
        val errorName: String,
        val errorValue: String,
        val traceBack: String
): CompletionResult(CompletionStatus.ERROR) {
    override fun toJson(): JsonObject {
        val res = super.toJson()
        res["ename"] = errorName
        res["evalue"] = errorValue
        res["traceback"] = traceBack
        return res
    }
}


class KotlinCompleter(private val ctx: KotlinContext) {
    fun complete(buf: String, cursor: Int): CompletionResult {
        try {
            val bounds = getTokenBounds(buf, cursor)
            val token = buf.substring(bounds.start, bounds.end)

            val tokens = TreeMap<String, String>()
            val tokensFilter = { t: String -> t.startsWith(token) }
            tokens.putAll(keywords.filter { entry -> tokensFilter(entry.key) })

            tokens.putAll(ctx.getVarsList().asSequence()
                    .filter { tokensFilter(it.name) }
                    .map { it.name to shorten(it.type) })

            tokens.putAll(ctx.getFunctionsList().asSequence()
                    .filter { tokensFilter(it.name) }
                    .map { it.name to it.toString(true) })

            return CompletionResultSuccess(tokens.keys.toList(), bounds, tokens)
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            return CompletionResultError(e.javaClass.simpleName, e.message ?: "", sw.toString())
        }
    }

    companion object {
        private val keywords = KotlinKeywords.KEYWORDS.asSequence().map { it to "keyword" }.toMap()

        fun getTokenBounds(buf: String, cursor: Int): CompletionTokenBounds {
            require(cursor <= buf.length) { "Position $cursor does not exist in code snippet <$buf>" }

            val startSubstring = buf.substring(0, cursor)
            val endSubstring = buf.substring(cursor)

            val filter = {c: Char -> !c.isLetterOrDigit()}

            val start = startSubstring.indexOfLast(filter) + 1
            var end = endSubstring.indexOfFirst(filter)
            end = if (end == -1) {
                buf.length
            } else {
                end + startSubstring.length
            }

            return CompletionTokenBounds(start, end)

        }
    }
}
