package ru.billyhargrove.pimobile.net

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import ru.billyhargrove.pimobile.core.*

/** Blocking transport; feature owners schedule it off the main thread. */
class HttpApi(private val client: OkHttpClient) {
    @Throws(IOException::class) fun health(baseUrl: String): String {
        val request = Request.Builder().url(EndpointPolicy.apiUrl(baseUrl, "/health")).get().header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            val body = bodyString(response)
            if (!response.isSuccessful) throw failure(response, body)
            body
        }
    }
    @Throws(Exception::class) fun transcribe(baseUrl: String, token: String, audio: File): String {
        return transcription(baseUrl, token, audio).execute()
    }
    internal interface Transcription { fun execute(): String; fun cancel() }
    /** Same blocking wire policy, with cancellation scoped to this one request. */
    internal fun transcription(baseUrl: String, token: String, audio: File): Transcription {
        if (!audio.isFile || !PcmAudio.validSize(audio.length()))
            throw IOException("Recording must be between 0.1 seconds and 10 minutes")
        val request = Request.Builder().url(EndpointPolicy.apiUrl(baseUrl, "/api/transcribe")).header("Authorization", "Bearer $token")
            .header("X-Audio-Sample-Rate", PcmAudio.SAMPLE_RATE.toString()).post(audio.asRequestBody("application/octet-stream".toMediaType())).build()
        val voiceClient = client.newBuilder().writeTimeout(90, TimeUnit.SECONDS).readTimeout(590, TimeUnit.SECONDS).callTimeout(600, TimeUnit.SECONDS).build()
        val call = voiceClient.newCall(request)
        return object : Transcription {
            override fun cancel() = call.cancel()
            override fun execute(): String = call.execute().use { response ->
                val value = bodyString(response)
                if (!response.isSuccessful) throw failure(response, value)
                JSONObject(value).getString("text")
            }
        }
    }
    @Throws(IOException::class) fun fetchCatalog(baseUrl: String, token: String): Catalog =
        CatalogParser.parse(getJson(EndpointPolicy.apiUrl(baseUrl, "/api/catalog"), token)) ?: throw ApiException("Unexpected catalog response from server")
    @Throws(IOException::class) fun fetchUsage(baseUrl: String, token: String) = getJson(EndpointPolicy.apiUrl(baseUrl, "/api/usage"), token)
    @Throws(IOException::class) fun fetchOrchestration(baseUrl: String, token: String, sessionId: String) =
        getJson(EndpointPolicy.apiUrl(baseUrl, "/api/sessions/${encodePathSegment(sessionId)}/orchestration"), token)
    @Throws(IOException::class) fun fetchAgent(baseUrl: String, token: String, sessionId: String, agentId: String, before: Long) =
        getJson(EndpointPolicy.apiUrl(baseUrl, "/api/sessions/${encodePathSegment(sessionId)}/orchestration/agents/${encodePathSegment(agentId)}" +
            if (before > 0) "?before=$before" else ""), token)
    @Throws(IOException::class) fun fetchArchive(baseUrl: String, token: String, offset: Int, query: String?) =
        getJson(EndpointPolicy.apiUrl(baseUrl, "/api/archive?offset=$offset&q=${encodePathSegment(query)}"), token)
    @Throws(IOException::class) fun fetchSnapshot(baseUrl: String, token: String, sessionId: String): Snapshot =
        SnapshotParser.parse(getJson(EndpointPolicy.apiUrl(baseUrl, "/api/sessions/${encodePathSegment(sessionId)}"), token))
            ?: throw ApiException("Unexpected session snapshot from server")
    @Throws(IOException::class) fun fetchMedia(baseUrl: String, token: String, rawUrl: String): ByteArray {
        val resolved = MediaUrlPolicy.resolve(baseUrl, rawUrl) ?: throw ApiException("Media URL points outside the configured server")
        val request = Request.Builder().url(resolved).get().header("Authorization", "Bearer $token").header("Accept", "image/*").build()
        return client.newBuilder().readTimeout(45, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw failure(response, bodyString(response))
            val body = response.body ?: throw ApiException("Empty image response")
            if (body.contentLength() > ImageGuard.MAX_TOTAL_BYTES) throw ApiException("Image exceeds 10 MB")
            readCapped(body.byteStream(), ImageGuard.MAX_TOTAL_BYTES, "Image exceeds 10 MB")
        }
    }
    @Throws(IOException::class) private fun getJson(url: String, token: String): JSONObject {
        val request = Request.Builder().url(url).get().header("Authorization", "Bearer $token").header("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            val body = bodyString(response)
            if (!response.isSuccessful) throw failure(response, body)
            try { JSONObject(body) } catch (cause: JSONException) { throw ApiException("Invalid JSON from server", response.code, cause) }
        }
    }
    private fun failure(response: Response, body: String?): ApiException {
        val hint = when (response.code) {
            401, 403 -> "Server rejected the token. Check your token in Settings."
            404 -> "Requested resource was not found."
            else -> "Server error: HTTP ${response.code}"
        }
        val error = if (body.isNullOrBlank()) "" else try { JSONObject(body).optString("error", "") } catch (_: JSONException) { "" }
        return ApiException(hint + if (error.isNotEmpty()) " ($error)" else "", response.code, null)
    }
    private fun bodyString(response: Response): String {
        return try {
            val body = response.body ?: return ""
            if (body.contentLength() > 1024 * 1024) "" else String(readCapped(body.byteStream(), 1024 * 1024L, "Response exceeds 1 MB"), Charsets.UTF_8)
        } catch (_: IOException) { "" }
    }
    private fun readCapped(input: InputStream, cap: Long, error: String): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024); var total = 0L
        while (true) { val count = input.read(buffer); if (count == -1) break; total += count; if (total > cap) throw ApiException(error); output.write(buffer, 0, count) }
        return output.toByteArray()
    }
    companion object { @JvmStatic fun encodePathSegment(value: String?) = if (value == null) "" else URLEncoder.encode(value, "UTF-8").replace("+", "%20") }
}
