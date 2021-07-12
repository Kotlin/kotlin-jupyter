package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState

// TODO : perhaps, create factory class
fun generateHTMLVarsReport(variablesState: Map<String, VariableState>): String {
    return buildString {
        append(
            """
            <!DOCTYPE html>
            <html>
            <head>
            
            """.trimIndent()
        )
        append(generateStyleSection())
        append("\n</head>\n<body>\n")
        append("<h2 style=\"text-align:center\">Variables State</h2>\n")

        if (variablesState.isEmpty()) {
            this.append("<p>Empty state</p>\n\n")
            this.append("</body>\n</html>")
            return this.toString()
        }

        append(generateVarsTable(variablesState))

        append("</body>\n</html>")
    }
}

// TODO: text is not aligned in the center
fun generateStyleSection(borderPx: Int = 1, paddingPx: Int = 5): String {
    //language=HTML
    val styleSection = """
    <style>
    table, th, td {
      border: ${borderPx}px solid black;
      border-collapse: collapse;
      text-align:center;
    }
    th, td {
      padding: ${paddingPx}px;
    }
    </style>
    """.trimIndent()
    return styleSection
}

fun generateVarsTable(variablesState: Map<String, VariableState>): String {
    return buildString {
        append(
            """
        <table style="width:80%" align="center">
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
                <td>${it.value.stringValue}</td>
            </tr>
                """.trimIndent()
            )
        }

        append("\n</table>\n")
    }
}
