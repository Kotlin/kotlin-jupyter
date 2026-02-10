package org.jetbrains.kotlinx.jupyter.repl.impl

class ImportsHolder {
    private val addedImports = mutableListOf<String>()

    fun popAddedImports(): List<String> {
        val res = addedImports.toList()
        addedImports.clear()
        return res
    }

    fun addImports(imports: List<String>) {
        addedImports.addAll(imports)
    }
}
