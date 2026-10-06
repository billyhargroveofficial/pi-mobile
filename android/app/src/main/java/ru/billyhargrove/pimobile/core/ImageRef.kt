package ru.billyhargrove.pimobile.core

class ImageRef(url: String?, mimeType: String?) {
    private val url = url.orEmpty()
    private val mimeType = mimeType.orEmpty()
    fun url() = url
    fun mimeType() = mimeType
    fun isUsable() = url.isNotEmpty()
    override fun equals(other: Any?): Boolean = other is ImageRef &&
        url == other.url && mimeType == other.mimeType
    override fun hashCode() = java.util.Objects.hash(url, mimeType)
    override fun toString() = "ImageRef{$url, $mimeType}"
}
