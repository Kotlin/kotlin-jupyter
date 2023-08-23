package org.jetbrains.kotlinx.jupyter.repl.impl

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.api.DeclarationKind
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDataCollector
import org.jetbrains.kotlinx.jupyter.compiler.ScriptDeclarationsCollectorInternal

class ScriptDeclarationsCollectorImpl : ScriptDeclarationsCollectorInternal {
    private var lastDeclarations: List<DeclarationInfo> = emptyList()

    override fun collect(scriptInfo: ScriptDataCollector.ScriptInfo) {
        if (!scriptInfo.isUserScript) return
        val source = scriptInfo.source
        if (source !is KtFileScriptSource) return

        val fileDeclarations = source.ktFile.declarations
        val scriptDeclaration = fileDeclarations[0] as? KtScript ?: return

        lastDeclarations = scriptDeclaration.declarations.map { declaration ->
            val kind = when (declaration) {
                is KtClass -> DeclarationKind.CLASS
                is KtObjectDeclaration -> DeclarationKind.OBJECT
                is KtProperty -> DeclarationKind.PROPERTY
                is KtFunction -> DeclarationKind.FUNCTION
                is KtScriptInitializer -> DeclarationKind.SCRIPT_INITIALIZER
                else -> DeclarationKind.UNKNOWN
            }
            DeclarationInfoImpl(declaration.name, kind)
        }
    }

    override fun getLastSnippetDeclarations(): List<DeclarationInfo> {
        return lastDeclarations
    }

    class DeclarationInfoImpl(
        override val name: String?,
        override val kind: DeclarationKind,
    ) : DeclarationInfo
}
