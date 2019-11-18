package org.jetbrains.kotlin.jupyter.repl.building

import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import org.jetbrains.kotlin.jupyter.repl.context.KotlinReceiver

/**
 * Class that holds properties for Kotlin REPL creation,
 * namely implicit receiver, classpath, preloaded code, directory for class bytecode output,
 * max result limit and shortening types flag.
 *
 * Set its parameters by chaining corresponding methods, e.g.
 * properties.outputDir(dir).shortenTypes(false)
 *
 * Get its parameters via getters.
 */
class KotlinReplProperties {

    val hostConf = defaultJvmScriptingHostConfiguration

    var receiver: KotlinReceiver? = null
        private set
    private val classpath: MutableSet<String>
    private val codeOnLoad: MutableList<String>
    var outputDir: String? = null
        private set
    var maxResult = 1000
        private set
    var shortenTypes = true
        private set

    init {
        this.receiver = KotlinReceiver()

        this.classpath = HashSet()
        val javaClasspath = System.getProperty("java.class.path").split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        Collections.addAll(classpath, *javaClasspath)

        this.codeOnLoad = ArrayList()
    }

    fun receiver(receiver: KotlinReceiver): KotlinReplProperties {
        this.receiver = receiver
        return this
    }

    fun classPath(path: String): KotlinReplProperties {
        this.classpath.add(path)
        return this
    }

    fun classPath(paths: Collection<String>): KotlinReplProperties {
        this.classpath.addAll(paths)
        return this
    }

    fun codeOnLoad(code: String): KotlinReplProperties {
        this.codeOnLoad.add(code)
        return this
    }

    fun codeOnLoad(code: Collection<String>): KotlinReplProperties {
        this.codeOnLoad.addAll(code)
        return this
    }

    fun outputDir(outputDir: String): KotlinReplProperties {
        this.outputDir = outputDir
        return this
    }

    fun maxResult(maxResult: Int): KotlinReplProperties {
        this.maxResult = maxResult
        return this
    }

    fun shortenTypes(shortenTypes: Boolean): KotlinReplProperties {
        this.shortenTypes = shortenTypes
        return this
    }

    fun getClasspath(): Set<String> {
        return classpath
    }

    fun getCodeOnLoad(): List<String> {
        return codeOnLoad
    }
}
