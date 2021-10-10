package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState

const val varsTableStyleClass = "variables_table"

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

private fun generateStyleSection(borderPx: Int = 1, paddingPx: Int = 5): String {
    //language=HTML
    return """
    <style>
    table.$varsTableStyleClass, .$varsTableStyleClass th, .$varsTableStyleClass td {
      border: ${borderPx}px solid black;
      border-collapse: collapse;
      text-align:center;
    }
    .$varsTableStyleClass th, .$varsTableStyleClass td {
      padding: ${paddingPx}px;
    }
    </style>
    
    """.trimIndent()
}

private fun generateVarsTable(variablesState: Map<String, VariableState>): String {
    return buildString {
        append(
            """
            <table class="$varsTableStyleClass" style="width:80%;margin-left:auto;margin-right:auto;" align="center">
              <tr>
                <th>Variable</th>
                <th>Value</th>
              </tr>
      
            """.trimIndent()
        )

        variablesState.entries.forEach {
            append(
                """
                <tr>
                    <td>${it.key}</td>
                    <td><pre>${it.value.stringValue}</pre></td>
                </tr>
                """.trimIndent()
            )
        }

        append("\n</table>\n")
    }
}
