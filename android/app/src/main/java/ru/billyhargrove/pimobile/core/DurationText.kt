package ru.billyhargrove.pimobile.core

object DurationText {
    @JvmStatic fun format(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return (if (hours > 0) "${hours}h " else "") +
            (if (hours > 0 || minutes > 0) "${minutes}m " else "") + "${seconds % 60}s"
    }
}
