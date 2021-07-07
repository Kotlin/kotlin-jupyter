package org.jetbrains.kotlinx.jupyter

import org.jetbrains.kotlinx.jupyter.api.VariableState

// TODO : perhaps, create factory class
fun generateHTMLVarsReport(variablesMap: Map<String, VariableState>): String {
    return buildString {
        this.append(
            """
            <!DOCTYPE html>
            <html>
            <head>
            
            """.trimIndent()
        )
        this.append(generateStyleSection())
        this.append("\n</head>\n<body>\n")
        this.append("<h2 style=\"text-align:center\">Variables State</h2>\n")

        if (variablesMap.isEmpty()) {
            this.append("<p>Empty state</p>\n\n")
            this.append("</body>\n</html>")
            return this.toString()
        }

        this.append(generateVarsTable(variablesMap))

        this.append("</body>\n</html>")
    }
}

// TODO: text is not aligning in a center
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

fun generateVarsTable(variablesMap: Map<String, VariableState>): String {
    return buildString {
        this.append(
            """
        <table style="width:80%" align="center">
          <tr>
            <th>Variable</th>
            <th>Value</th>
          </tr>
      
            """.trimIndent()
        )

        variablesMap.entries.forEach {
            this.append(
                """
            <tr>
                <td>${it.key}</td>
                <td>${it.value.stringValue}</td>
            </tr>
                """.trimIndent()
            )
        }

        this.append("\n</table>\n")
    }
}
