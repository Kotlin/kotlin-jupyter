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

package org.jetbrains.kotlin.jupyter.repl.building

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmReplCompilerImpl
import java.io.File
import java.util.StringJoiner
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvmhost.repl.JvmReplCompiler
import kotlin.script.experimental.jvmhost.repl.JvmReplEvaluator

/**
 * Util class for building REPL components.
 */
object ReplBuilding {
    fun buildCompiler(properties: KotlinReplProperties): JvmReplCompiler {
        val receiverClassPath = properties.receiver!!.javaClass
                .protectionDomain.codeSource.location.path
        properties.classPath(receiverClassPath)

        val compilerImpl = KJvmReplCompilerImpl(properties.hostConf)

        return JvmReplCompiler(
                buildCompilationConfiguration(properties),
                properties.hostConf,
                compilerImpl)
    }

    fun buildEvaluator(properties: KotlinReplProperties): JvmReplEvaluator {
        return JvmReplEvaluator(
                buildEvaluationConfiguration(properties),
                BasicJvmScriptEvaluator())
    }

    private fun buildClassPath(p: KotlinReplProperties): String {
        val joiner = StringJoiner(File.pathSeparator)
        for (path in p.getClasspath()) {
            if (path != "") {
                joiner.add(path)
            }
        }
        return joiner.toString()
    }

    private fun buildCompilationConfiguration(
            p: KotlinReplProperties): ScriptCompilationConfiguration {
        return ScriptCompilationConfiguration {
            hostConfiguration.invoke(p.hostConf)

            val jvmBuilder = jvm
            jvmBuilder.dependenciesFromCurrentContext(wholeClasspath = true, unpackJarCollections = false)

            val compilerOptions = listOf("-classpath", buildClassPath(p))

            this.compilerOptions.invoke(compilerOptions)

            val kt = KotlinType(p.receiver!!.javaClass.canonicalName)
            val receivers = listOf(kt)
            implicitReceivers.invoke(receivers)

            Unit
        }
    }

    private fun buildEvaluationConfiguration(
            p: KotlinReplProperties): ScriptEvaluationConfiguration {
        return ScriptEvaluationConfiguration {
            hostConfiguration.invoke(p.hostConf)

            val receivers = listOf<Any?>(p.receiver)
            implicitReceivers.invoke(receivers)

            Unit
        }
    }
}
