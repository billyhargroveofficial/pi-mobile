package ru.billyhargrove.pimobile.core

object ImageGuard {
    const val MAX_IMAGES = 3
    const val MAX_TOTAL_BYTES = 10L * 1024 * 1024
    @JvmField val ALLOWED_MIME_TYPES: Set<String> = java.util.Collections.unmodifiableSet(setOf("image/png", "image/jpeg", "image/webp"))
    @JvmStatic fun isAllowedMimeType(mime: String?) = mime?.trim()?.lowercase(java.util.Locale.ROOT) in ALLOWED_MIME_TYPES
    @JvmStatic fun totalBytes(images: List<ImagePayload?>?) = images.orEmpty().sumOf { it?.size()?.toLong() ?: 0L }
    @JvmStatic fun remainingBytes(images: List<ImagePayload?>?) = (MAX_TOTAL_BYTES - totalBytes(images)).coerceAtLeast(0)
    @JvmStatic fun validate(images: List<ImagePayload?>?) {
        if (images.isNullOrEmpty()) return
        require(images.size <= MAX_IMAGES) { "No more than $MAX_IMAGES images per message" }
        for (image in images) {
            requireNotNull(image) { "Empty image" }
            require(isAllowedMimeType(image.mimeType())) { "Unsupported format: ${image.mimeType()}" }
            require(image.size() > 0) { "Empty image file" }
        }
        val total = totalBytes(images)
        require(total <= MAX_TOTAL_BYTES) { "Total image size of ${total / (1024 * 1024)} MB exceeds the 10 MB limit" }
    }
}
