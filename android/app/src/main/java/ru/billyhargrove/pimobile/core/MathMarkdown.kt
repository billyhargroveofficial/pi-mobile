package ru.billyhargrove.pimobile.core

/** Normalize math delimiters without touching fenced/inline code or prices. */
object MathMarkdown {
    @JvmStatic fun normalize(source: String): String {
        val out = StringBuilder()
        var fence = false; var fenceToken = ""
        for (line in source.split('\n')) {
            val trim = line.trim()
            if (trim.startsWith("```") || trim.startsWith("~~~")) {
                val token = trim.substring(0, 3)
                if (!fence) { fence = true; fenceToken = token } else if (token == fenceToken) fence = false
                out.append(line).append('\n'); continue
            }
            if (fence) { out.append(line).append('\n'); continue }
            var code = false; var index = 0
            while (index < line.length) {
                val char = line[index]
                if (char == '`') { code = !code; out.append(char); index++; continue }
                if (code) { out.append(char); index++; continue }
                if (line.startsWith("\\[", index) || line.startsWith("\\]", index)) {
                    out.append("\n$$\n"); index += 2; continue
                }
                if (line.startsWith("\\(", index) || line.startsWith("\\)", index)) {
                    out.append("$$"); index += 2; continue
                }
                if (char == '$' && (index == 0 || line[index - 1] != '\\')) {
                    if (index + 1 < line.length && line[index + 1] == '$') { out.append("$$"); index += 2; continue }
                    val end = line.indexOf('$', index + 1)
                    if (end > index + 1 && !Character.isWhitespace(line[index + 1]) && !Character.isWhitespace(line[end - 1])) {
                        out.append("$$").append(line, index + 1, end).append("$$"); index = end + 1; continue
                    }
                }
                out.append(char); index++
            }
            out.append('\n')
        }
        return if (out.isEmpty()) "" else out.substring(0, out.length - 1)
    }
}
