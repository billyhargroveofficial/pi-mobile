package ru.billyhargrove.pimobile.net

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import ru.billyhargrove.pimobile.core.ImagePayload
import ru.billyhargrove.pimobile.media.ImagePreparer

/** Imports only explicitly picked URIs and caps bytes during reading. */
object AttachmentPreparer {
    @JvmStatic @Throws(Exception::class)
    fun prepare(resolver: ContentResolver, uri: Uri, budget: Long): ImagePayload {
        if (resolver.getType(uri)?.startsWith("image/") == true) return ImagePreparer.prepare(resolver, uri, budget)
        val name = ImagePreparer.displayName(resolver, uri).ifBlank { "file" }
            .map { if (it == '/' || it == '\\' || it.code < 32) '_' else it }.joinToString("").take(200)
        return resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0L
            while (true) {
                val count = input.read(buffer); if (count == -1) break
                total += count
                if (total > budget) throw IOException("Attachments must total no more than 10 MB")
                output.write(buffer, 0, count)
            }
            if (total == 0L) throw IOException("Empty file")
            ImagePayload.file(output.toByteArray(), name)
        } ?: throw IOException("Could not open file")
    }
}
