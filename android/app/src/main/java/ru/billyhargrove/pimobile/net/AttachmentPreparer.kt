package ru.billyhargrove.pimobile.net

import android.content.ContentResolver
import android.net.Uri
import ru.billyhargrove.pimobile.core.ImagePayload
import ru.billyhargrove.pimobile.media.PickedAttachments

/** Imports only explicitly picked URIs and caps bytes during reading. */
object AttachmentPreparer {
    @JvmStatic @Throws(Exception::class)
    fun prepare(resolver: ContentResolver, uri: Uri, budget: Long): ImagePayload {
        return PickedAttachments(resolver).payload(uri, budget)
    }
}
