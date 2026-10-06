package ru.billyhargrove.pimobile.core

/** Defensively copied bytes; files retain their original name for gateway validation. */
class ImagePayload(data: ByteArray?, mimeType: String?) {
    private val data = data?.copyOf() ?: byteArrayOf()
    private val mimeType = mimeType.orEmpty()
    private var fileName: String? = null
    fun bytes() = data.copyOf()
    fun mimeType() = mimeType
    fun size() = data.size
    val isFile: Boolean get() = fileName != null
    fun fileName() = fileName
    companion object {
        @JvmStatic fun file(data: ByteArray?, name: String?) = ImagePayload(data, "application/octet-stream").apply { fileName = name }
    }
}
