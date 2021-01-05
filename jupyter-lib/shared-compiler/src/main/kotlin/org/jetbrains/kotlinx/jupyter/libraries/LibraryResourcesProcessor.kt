package org.jetbrains.kotlinx.jupyter.libraries

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.jupyter.api.Code
import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResource
import org.jetbrains.kotlinx.jupyter.api.libraries.ResourcePathType
import java.io.File

class LibraryResourcesProcessor {
    private var outputCounter = 0

    private fun loadResourceAsText(resource: LibraryResource, classLoader: ClassLoader): List<ScriptModifierFunctionGenerator> {
        return resource.locations.map { location ->
            val pathString = location.path
            when (location.type) {
                ResourcePathType.URL -> {
                    URLScriptModifierFunctionGenerator(pathString)
                }
                ResourcePathType.LOCAL_PATH -> {
                    CodeScriptModifierFunctionGenerator(File(pathString).readText())
                }
                ResourcePathType.CLASSPATH_PATH -> {
                    CodeScriptModifierFunctionGenerator(classLoader.getResource(pathString)?.readText().orEmpty())
                }
            }
        }
    }

    fun wrapLibrary(resource: LibraryResource, classLoader: ClassLoader): String {
        val resourceName = resource.name
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
            if(!window.kotlinQueues.$resourceName) {
                var resQueue = [];
                window.kotlinQueues.$resourceName = resQueue;
                window.call_$resourceName = function(f) {
                    resQueue.push(f);
                }
            }
            (function (){
                var modifiers = $jsScriptModifiers
                var e = document.getElementById("$elementId");
                modifiers.forEach(function (gen) {
                    var script = document.createElement("script");
                    gen(script)
                    script.type = "text/javascript";
                    script.onload = function() {
                        window.call_$resourceName = function(f) {f();};
                        window.kotlinQueues.forEach(function(f) {f();});
                        window.kotlinQueues = [];
                    };
                    script.onerror = function() {
                        window.call_$resourceName = function(f) {};
                        window.kotlinQueues = [];
                        var div = document.createElement("div");
                        div.style.color = 'darkred';
                        div.textContent = 'Error loading resource $resourceName';
                        document.getElementById("$elementId").appendChild(div);
                    };
                    
                    e.appendChild(script);
                })
            })()
        """.trimIndent()

        // language=html
        return """
            <div id="$elementId"/>
            <script type="text/javascript">
                $wrapper
            </script>
        """.trimIndent()
    }

    interface ScriptModifierFunctionGenerator {
        fun getScriptText(): String
    }

    class CodeScriptModifierFunctionGenerator(
        val code: Code
    ) : ScriptModifierFunctionGenerator {
        override fun getScriptText(): String {
            val escapedCode = Json.encodeToString(code)
            // language=js
            return """
                (function(script) {
                    script.textContent = "$escapedCode"
                })
            """.trimIndent()
        }
    }

    class URLScriptModifierFunctionGenerator(
        private val url: String
    ) : ScriptModifierFunctionGenerator {
        override fun getScriptText(): String {
            // language=js
            return """
                (function(script) {
                    script.src = "$url"
                })
            """.trimIndent()
        }
    }
}
