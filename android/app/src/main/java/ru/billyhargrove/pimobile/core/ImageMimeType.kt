package ru.billyhargrove.pimobile.core

/** Trust magic bytes, never the picker MIME or file extension. */
object ImageMimeType {
    const val PNG = "image/png"
    const val JPEG = "image/jpeg"
    const val WEBP = "image/webp"
    const val UNKNOWN = ""
    @JvmStatic fun detect(data: ByteArray?): String {
        if (data == null || data.size < 12) return UNKNOWN
        fun byte(index: Int) = data[index].toInt() and 0xff
        if (byte(0) == 0x89 && byte(1) == 80 && byte(2) == 78 && byte(3) == 71) return PNG
        if (byte(0) == 0xff && byte(1) == 0xd8 && byte(2) == 0xff) return JPEG
        if (byte(0) == 82 && byte(1) == 73 && byte(2) == 70 && byte(3) == 70 &&
            byte(8) == 87 && byte(9) == 69 && byte(10) == 66 && byte(11) == 80) return WEBP
        return UNKNOWN
    }
    @JvmStatic fun compressFormat(mimeType: String?) = if (mimeType == PNG) "PNG" else "JPEG"
}
