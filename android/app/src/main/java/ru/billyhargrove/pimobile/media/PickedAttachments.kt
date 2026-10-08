package ru.billyhargrove.pimobile.media

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import ru.billyhargrove.pimobile.core.ImageGuard
import ru.billyhargrove.pimobile.core.ImagePayload

/** Android metadata/byte/thumbnail adapter. Each picked entry queries its display name once. */
internal class PickedAttachments(private val resolver: ContentResolver) {
    fun prepare(uri: Uri, budget: Long, reader: AttachmentRead): Attachment {
        requireBudget(budget); reader.check()
        val name = ImagePreparer.displayName(resolver, uri)
        val payload = payload(uri, budget, reader, name)
        reader.check()
        val thumbnail = if (payload.isFile) null else ImagePreparer.thumbnail(payload.bytes(), 320)
        try { reader.check(); return Attachment(payload, thumbnail, name) }
        catch (cause: Exception) { thumbnail?.recycle(); throw cause }
    }
    fun payload(uri: Uri, budget: Long, reader: AttachmentRead = AttachmentRead(), name: String? = null): ImagePayload {
        requireBudget(budget); reader.check()
        if (resolver.getType(uri)?.startsWith("image/") == true) {
            val bytes = reader.read(ImageGuard.MAX_TOTAL_BYTES + 1, "Файл больше 10 МБ") { resolver.openInputStream(uri) }
            return ImagePreparer.prepare(bytes, budget, reader::check)
        }
        val fileName = (name ?: ImagePreparer.displayName(resolver, uri)).ifBlank { "file" }
            .take(200).map { if (it == '/' || it == '\\' || it.code < 32) '_' else it }.joinToString("")
        val bytes = reader.read(budget.coerceAtMost(ImageGuard.MAX_TOTAL_BYTES), "Attachments must total no more than 10 MB") {
            resolver.openInputStream(uri)
        }
        return ImagePayload.file(bytes, fileName)
    }
    private fun requireBudget(budget: Long) { if (budget <= 0) throw IOException("Attachments must total no more than 10 MB") }
}
