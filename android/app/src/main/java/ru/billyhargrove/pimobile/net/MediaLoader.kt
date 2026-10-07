package ru.billyhargrove.pimobile.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import ru.billyhargrove.pimobile.core.MediaUrlPolicy
import ru.billyhargrove.pimobile.store.SettingsStore
import ru.billyhargrove.pimobile.store.ConversationCache

/** Authenticated same-origin images. Bounded decode and memory cache; no image files. */
class MediaLoader internal constructor(private val api: HttpApi, private val connection: () -> Connection) {
    internal data class Connection(val base: String, val token: String)
    constructor(api: HttpApi, settings: SettingsStore) : this(api, { Connection(settings.baseUrl(), settings.token()) })
    interface Callback {
        fun onLoaded(resolvedUrl: String, bitmap: Bitmap)
        fun onFailed(resolvedUrl: String, message: String)
    }
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap) = bitmap.byteCount
    }
    private val lock = Any()
    private val inFlight = mutableMapOf<String, MutableList<Callback>>()
    fun load(rawUrl: String, callback: Callback) {
        val (base, token) = connection()
        val resolved = MediaUrlPolicy.resolve(base, rawUrl)
        if (resolved == null) {
            AppExecutors.main { callback.onFailed("", "External source blocked: image is not from this server") }
            return
        }
        // URL alone must not share bytes or an in-flight authorization failure
        // across credential changes. Keep secrets out of cache identifiers.
        val key = ConversationCache.key(base, token, resolved)
        synchronized(lock) {
            cache.get(key)?.let { bitmap -> AppExecutors.main { callback.onLoaded(resolved, bitmap) }; return }
            inFlight[key]?.let { it.add(callback); return }
            inFlight[key] = mutableListOf(callback)
        }
        AppExecutors.io().execute {
            var result: Bitmap? = null; var failure = "Loading failed"
            try {
                result = decode(api.fetchMedia(base, token, rawUrl))
                if (result == null) failure = "Could not decode image"
            } catch (error: Exception) { failure = error.message ?: "Could not load image" }
            val bitmap = result; val error = failure
            val callbacks = synchronized(lock) {
                if (bitmap != null) cache.put(key, bitmap)
                inFlight.remove(key)?.toList().orEmpty()
            }
            AppExecutors.main { callbacks.forEach { if (bitmap != null) it.onLoaded(resolved, bitmap) else it.onFailed(resolved, error) } }
        }
    }
    fun clearCache() { synchronized(lock) { cache.evictAll() } }
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        return try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        }) } catch (_: OutOfMemoryError) { null }
    }
}
