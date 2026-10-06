package ru.billyhargrove.pimobile.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import ru.billyhargrove.pimobile.core.ImageGuard
import ru.billyhargrove.pimobile.core.ImageMimeType
import ru.billyhargrove.pimobile.core.ImagePayload

/** Picked-image importer: sniff bytes, decode with a size cap, fit the shared budget. */
object ImagePreparer {
    @JvmStatic @Throws(IOException::class)
    fun prepare(resolver: ContentResolver?, uri: Uri?, budgetBytes: Long): ImagePayload {
        if (resolver == null || uri == null) throw IOException("Файл недоступен")
        val budget = budgetBytes.coerceIn(64 * 1024L, ImageGuard.MAX_TOTAL_BYTES)
        val raw = readUpTo(resolver, uri, ImageGuard.MAX_TOTAL_BYTES + 1)
        if (raw.isEmpty()) throw IOException("Пустой файл")
        val mime = ImageMimeType.detect(raw)
        if (mime.isEmpty()) throw IOException("Формат не поддерживается: нужен PNG, JPEG или WebP")
        if (raw.size <= budget) return ImagePayload(raw, mime)
        val source = decode(raw, 2048) ?: throw IOException("Не удалось декодировать изображение")
        val format = if (ImageMimeType.compressFormat(mime) == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        try {
            for (edge in intArrayOf(2048, 1024, 512)) {
                val largest = maxOf(source.width, source.height)
                val scaled = if (largest <= edge) source else {
                    val ratio = edge.toFloat() / largest
                    Bitmap.createScaledBitmap(source, (source.width * ratio).roundToInt().coerceAtLeast(1),
                        (source.height * ratio).roundToInt().coerceAtLeast(1), true)
                }
                try {
                    for (quality in if (format == Bitmap.CompressFormat.PNG) intArrayOf(100) else intArrayOf(85, 70, 55, 40)) {
                        val output = ByteArrayOutputStream()
                        if (!scaled.compress(format, quality, output)) throw IOException("Не удалось кодировать изображение")
                        val bytes = output.toByteArray()
                        if (bytes.size <= budget) return ImagePayload(bytes, ImageMimeType.detect(bytes))
                    }
                } finally { if (scaled !== source) scaled.recycle() }
            }
        } finally { source.recycle() }
        throw IOException("Не удалось ужать изображение до лимита 10 МБ")
    }
    @JvmStatic fun thumbnail(bytes: ByteArray?, maxEdge: Int): Bitmap? =
        if (bytes == null || bytes.isEmpty()) null else decode(bytes, maxEdge.coerceAtLeast(1))
    @JvmStatic fun displayName(resolver: ContentResolver?, uri: Uri?): String {
        if (resolver == null || uri == null) return "изображение"
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.takeIf { it.isNotEmpty() }?.let { return it }
                }
            }
        } catch (_: RuntimeException) { }
        return "изображение"
    }
    private fun decode(raw: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        return try { BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) }
        catch (_: OutOfMemoryError) { null }
    }
    private fun readUpTo(resolver: ContentResolver, uri: Uri, cap: Long): ByteArray =
        resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0L
            while (true) {
                val count = input.read(buffer); if (count == -1) break
                total += count; if (total > cap) throw IOException("Файл больше 10 МБ")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IOException("Не удалось открыть файл")
}
