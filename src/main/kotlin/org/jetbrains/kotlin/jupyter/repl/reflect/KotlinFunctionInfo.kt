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

package org.jetbrains.kotlin.jupyter.repl.reflect

import kotlin.reflect.KFunction

import org.jetbrains.kotlin.jupyter.repl.reflect.KotlinReflectUtil.functionSignature
import org.jetbrains.kotlin.jupyter.repl.reflect.KotlinReflectUtil.shorten


class KotlinFunctionInfo(private val function: KFunction<*>) : Comparable<KotlinFunctionInfo> {

    val name: String
        get() = function.name

    fun toString(shortenTypes: Boolean): String {
        return if (shortenTypes) {
            shorten(toString())
        } else toString()
    }

    override fun toString(): String {
        return functionSignature(function)
    }

    override fun compareTo(other: KotlinFunctionInfo): Int {
        return this.toString().compareTo(other.toString())
    }

    override fun hashCode(): Int {
        return this.toString().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is KotlinFunctionInfo) {
            this.toString() == other.toString()
        } else false
    }
}
