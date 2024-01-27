package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourceFallbacksBundle
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import org.jetbrains.kotlinx.jupyter.common.getHttp
import org.jetbrains.kotlinx.jupyter.config.getLogger
import java.io.File
import java.io.IOException

class JsLibraryResourcesProcessor : LibraryResourcesProcessor {
    private var outputCounter = 0

    private fun loadBunch(bundle: ResourceFallbacksBundle, classLoader: ClassLoader): ScriptModifierFunctionGenerator {
        val exceptions = mutableListOf<Exception>()
        for (resourceLocation in bundle.locations) {
            val path = resourceLocation.path

            return try {
                when (resourceLocation.type) {
                    ResourcePathType.URL -> {
                        URLScriptModifierFunctionGenerator(path)
                    }
                    ResourcePathType.URL_EMBEDDED -> {
                        val scriptText = getHttp(path).text
                        CodeScriptModifierFunctionGenerator(scriptText)
                    }
                    ResourcePathType.LOCAL_PATH -> {
                        val file = File(path)
                        logger.debug("Resolving resource file: ${file.absolutePath}")
                        CodeScriptModifierFunctionGenerator(file.readText())
                    }
                    ResourcePathType.CLASSPATH_PATH -> {
                        val content = loadResourceFromClassLoader(path, classLoader)
                        return CodeScriptModifierFunctionGenerator(content)
                    }
                }
            } catch (e: IOException) {
                exceptions.add(e)
                continue
            }
        }

        throw Exception("No resource fallback found! Related exceptions: $exceptions")
    }

    private fun loadResourceAsText(resource: LibraryResource, classLoader: ClassLoader): List<ScriptModifierFunctionGenerator> {
        return resource.bundles.map { loadBunch(it, classLoader) }
    }

    override fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        val resourceName = resource.name
        val resourceIndex = """["$resourceName"]"""
        val callResourceIndex = """["call_$resourceName"]"""
        val elementId = "kotlin_out_$outputCounter"
        ++outputCounter

        val generators = loadResourceAsText(resource, classLoader)
        val jsScriptModifiers = generators.joinToString(",\n", "[", "]") {
            it.getScriptText()
        }

        @Language("js")
        val wrapper = """
            if(!window.kotlinQueues) {
                window.kotlinQueues = {};
            }
            if(!window.kotlinQueues$resourceIndex) {
                var resQueue = [];
                window.kotlinQueues$resourceIndex = resQueue;
                window$callResourceIndex = function(f) {
                    resQueue.push(f);
                }
            }
            (function (){
                var modifiers = $jsScriptModifiers;
                var e = document.getElementById("$elementId");
                modifiers.forEach(function (gen) {
                    var script = document.createElement("script");
                    gen(script)
                    script.addEventListener("load", function() {
                        window$callResourceIndex = function(f) {f();};
                        window.kotlinQueues$resourceIndex.forEach(function(f) {f();});
                        window.kotlinQueues$resourceIndex = [];
                    }, false);
                    script.addEventListener("error", function() {
                        window$callResourceIndex = function(f) {};
                        window.kotlinQueues$resourceIndex = [];
                        var div = document.createElement("div");
                        div.style.color = 'darkred';
                        div.textContent = 'Error loading resource $resourceName';
                        document.getElementById("$elementId").appendChild(div);
                    }, false);
                    
                    e.appendChild(script);
                });
            })();
        """.trimIndent()

        // language=html
        return """
            <div id="$elementId"></div>
            <script type="text/javascript">
                $wrapper
            </script>
        """.trimIndent()
    }

    private interface ScriptModifierFunctionGenerator {
        fun getScriptText(): String
    }

    private class CodeScriptModifierFunctionGenerator(
        val code: Code,
    ) : ScriptModifierFunctionGenerator {
        override fun getScriptText(): String {
            val escapedCode = Json.encodeToString(code)
            // language=js
            return """
                (function(script) {
                    script.textContent = $escapedCode
                    script.type = "text/javascript";
                })
            """.trimIndent()
        }
    }

    private class URLScriptModifierFunctionGenerator(
        private val url: String,
    ) : ScriptModifierFunctionGenerator {
        override fun getScriptText(): String {
            // language=js
            return """
                (function(script) {
                    script.src = "$url"
                    script.type = "text/javascript";
                })
            """.trimIndent()
        }
    }

    private val logger = getLogger(JsLibraryResourcesProcessor::class.simpleName!!)
}
