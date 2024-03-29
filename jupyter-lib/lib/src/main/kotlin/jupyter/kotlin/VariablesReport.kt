package jupyter.kotlin

import org.jetbrains.kotlinx.jupyter.api.MimeTypedResult
import org.jetbrains.kotlinx.jupyter.api.Notebook
import org.jetbrains.kotlinx.jupyter.api.VariableState
import org.jetbrains.kotlinx.jupyter.api.htmlResult

const val VARIABLES_TABLE_STYLE_CLASS = "variables_table"

val Notebook.variablesReportAsHTML: MimeTypedResult get() {
    return htmlResult(generateHTMLVarsReport(variablesState))
}

val Notebook.variablesReport: String get() {
    return if (variablesState.isEmpty()) {
        ""
    } else {
        buildString {
            append("Visible vars: \n")
            variablesState.forEach { (name, currentState) ->
                append("\t$name : ${currentState.stringValue}\n")
            }
        }
    }
}

fun generateHTMLVarsReport(variablesState: Map<String, VariableState>): String {
    return buildString {
        append(generateStyleSection())
        if (variablesState.isEmpty()) {
            append("<h2 style=\"text-align:center;\">Variables State's Empty</h2>\n")
            return toString()
        }

        append("<h2 style=\"text-align:center;\">Variables State</h2>\n")
        append(generateVarsTable(variablesState))
    }
}

private fun generateStyleSection(
    borderPx: Int = 1,
    paddingPx: Int = 5,
): String {
    //language=HTML
    return """
        <style>
        table.$VARIABLES_TABLE_STYLE_CLASS, .$VARIABLES_TABLE_STYLE_CLASS th, .$VARIABLES_TABLE_STYLE_CLASS td {
          border: ${borderPx}px solid black;
          border-collapse: collapse;
          text-align:center;
        }
        .$VARIABLES_TABLE_STYLE_CLASS th, .$VARIABLES_TABLE_STYLE_CLASS td {
          padding: ${paddingPx}px;
        }
        </style>
        
        """.trimIndent()
}

private fun generateVarsTable(variablesState: Map<String, VariableState>): String {
    return buildString {
        append(
            """
            <table class="$VARIABLES_TABLE_STYLE_CLASS" style="width:80%;margin-left:auto;margin-right:auto;" align="center">
              <tr>
                <th>Variable</th>
                <th>Value</th>
              </tr>
            
            """.trimIndent(),
        )

        variablesState.entries.forEach {
            append(
                """
                <tr>
                    <td>${it.key}</td>
                    <td><pre>${it.value.stringValue}</pre></td>
                </tr>
                """.trimIndent(),
            )
        }

        append("\n</table>\n")
    }
}
