package ru.billyhargrove.pimobile.media

import android.graphics.Bitmap
import ru.billyhargrove.pimobile.core.ImagePayload

/** Bytes stay in the outbox owner until a receipt or explicit removal releases them. */
class Attachment(private val payload: ImagePayload, private val thumbnail: Bitmap?, displayName: String?) {
    private val displayName = displayName ?: "изображение"
    fun payload() = payload
    fun thumbnail() = thumbnail
    fun displayName() = displayName
}
