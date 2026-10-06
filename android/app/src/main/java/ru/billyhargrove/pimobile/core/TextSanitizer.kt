package ru.billyhargrove.pimobile.core

/** No HTML; drop controls, cap blank lines, replace unpaired UTF-16 surrogates. */
object TextSanitizer {
    @JvmStatic fun safe(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val out = StringBuilder(raw.length)
        var newlines = 0
        var index = 0
        while (index < raw.length) {
            val c = raw[index++]
            when {
                c == '\r' -> continue
                Character.isHighSurrogate(c) -> {
                    if (index < raw.length && Character.isLowSurrogate(raw[index])) out.append(c).append(raw[index++])
                    else out.append('\uFFFD')
                }
                Character.isLowSurrogate(c) -> out.append('\uFFFD')
                c == '\n' -> { newlines++; if (newlines <= 3) out.append(c) }
                else -> { newlines = 0; if (c == '\t' || (c >= ' ' && c != '\u007F')) out.append(c) }
            }
        }
        var end = out.length
        while (end > 0 && out[end - 1] in "\n ") end--
        return out.substring(0, end)
    }
}
