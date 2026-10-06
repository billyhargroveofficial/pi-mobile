package ru.billyhargrove.pimobile.store

import java.io.File
import java.nio.file.*
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Disposable bounded private snapshots; serial callers and synchronized file access. */
class ConversationCache(private val directory: File) {
    companion object {
        private const val MAX_BYTES = 2L * 1024 * 1024
        private const val TTL = 7L * 24 * 60 * 60 * 1000
        @JvmStatic fun key(endpoint: String, token: String, session: String): String = try {
            MessageDigest.getInstance("SHA-256").digest("$endpoint\n$token\n$session".toByteArray(Charsets.UTF_8))
                .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
        } catch (cause: Exception) { throw IllegalStateException(cause) }
    }
    init { directory.mkdirs() }
    private fun file(key: String): File { require(Regex("[a-f0-9]{64}").matches(key)) { "Invalid cache key" }; return File(directory, "$key.json") }
    @Synchronized fun read(key: String): JSONObject? {
        val file = file(key)
        return try {
            if (!file.isFile || file.length() > MAX_BYTES || System.currentTimeMillis() - file.lastModified() > TTL) { file.delete(); return null }
            JSONObject(String(Files.readAllBytes(file.toPath()), Charsets.UTF_8)).takeIf {
                it.optString("type") == "snapshot" && it.optJSONArray("messages") != null
            }
        } catch (_: Exception) { file.delete(); null }
    }
    @Synchronized fun write(key: String, text: String) {
        val target = file(key); val temp = File(directory, "$key.tmp")
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > MAX_BYTES) { target.delete(); return }
            directory.mkdirs(); Files.write(temp.toPath(), bytes)
            try { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            directory.listFiles { _, name -> name.endsWith(".json") }?.sortedByDescending(File::lastModified)?.forEachIndexed { index, file ->
                if (index >= 8 || System.currentTimeMillis() - file.lastModified() > TTL) file.delete()
            }
        } catch (_: Exception) { /* Cache loss must never break the connection or expose partial state. */ }
        finally { temp.delete() }
    }
}
