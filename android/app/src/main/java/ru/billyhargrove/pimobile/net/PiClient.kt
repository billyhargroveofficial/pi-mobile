package ru.billyhargrove.pimobile.net

import android.os.Handler
import android.os.Looper
import okhttp3.*
import okio.ByteString
import org.json.JSONException
import org.json.JSONObject
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.store.ConversationCache
import java.io.File
import java.util.Random
import java.util.UUID
import java.util.concurrent.Executors

/** One gateway socket. Reconnect restores a subscription, never a pending command. */
class PiClient(private val http: OkHttpClient, private val api: HttpApi) : WebSocketListener() {
    interface Listener {
        fun onConnectionState(state: ConnectionState, detail: String)
        fun onCatalog(catalog: Catalog)
        fun onSnapshot(snapshot: Snapshot)
        fun onAck(ack: Ack)
        fun onMessages(update: MessagesUpdate) {}
        fun onConfiguration(sessionId: String, configuration: JSONObject) {}
        fun onTimelineMeta(frame: JSONObject) {}
        fun onData(requestId: String, sessionId: String, data: JSONObject) {}
        fun onCommandUncertain(requestId: String, sessionId: String, reason: String) {}
        fun onProtocolError(message: String) {}
    }
    private data class Pending(val requestId: String, val sessionId: String, val timeout: Runnable, val abort: Boolean)
    private val main = Handler(Looper.getMainLooper())
    private val random = Random()
    private val pending = linkedMapOf<String, Pending>()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var shuttingDown = true
    private var listener: Listener? = null
    private var baseUrl = ""
    private var token = ""
    private var desiredSessionId: String? = null
    private var state = ConnectionState.IDLE
    private var stateDetail = ""
    private var attempt = 0
    private var authRejected = false
    private var catalog = Catalog.empty()
    private var lastSnapshot: Snapshot? = null
    private var configuration: JSONObject? = null
    private var configurationSessionId = ""
    private var reconnectTask: Runnable? = null
    private var diskCache: ConversationCache? = null
    private val cacheIo = Executors.newSingleThreadExecutor()
    private val conversations = LinkedHashMap<String, JSONObject>(8, .75f, true)
    private var subscriptionGeneration = 0
    private var subscriptionReady = false
    private val pendingCacheWrites = hashSetOf<String>()

    fun cacheDirectory(directory: File) { diskCache = ConversationCache(directory) }
    private fun cacheKey(session: String) = ConversationCache.key(baseUrl, token, session)
    private fun retain(key: String, value: JSONObject) {
        conversations[key] = value
        while (conversations.size > 8) conversations.remove(conversations.keys.first())
        if (diskCache != null && pendingCacheWrites.add(key)) main.postDelayed({
            pendingCacheWrites.remove(key)
            val latest = conversations[key]; val cache = diskCache
            if (latest != null && cache != null && !cacheIo.isShutdown) {
                val json = latest.toString(); cacheIo.execute { cache.write(key, json) }
            }
        }, 750)
    }
    fun cachedViewport(session: String): JSONObject? = conversations[cacheKey(session)]?.optJSONObject("viewport")
    fun saveViewport(session: String, anchor: String, offset: Int, follow: Boolean) {
        val key = cacheKey(session); val value = conversations[key] ?: return
        try { value.put("viewport", JSONObject().put("anchor", anchor).put("offset", offset).put("follow", follow)); retain(key, value) }
        catch (_: JSONException) { }
    }
    private fun cacheFrame(frame: JSONObject) {
        val session = frame.optString("sessionId"); if (session.isEmpty()) return
        val key = cacheKey(session); val prior = conversations[key]
        try {
            val next = ConversationFrames.merge(prior, frame)
            if (prior != null && prior.has("viewport")) next.put("viewport", prior.get("viewport"))
            retain(key, next)
        } catch (_: JSONException) { }
    }
    private fun sendSubscription() {
        val ws = socket ?: return; val session = desiredSessionId ?: return
        if (!subscriptionReady || state != ConnectionState.CONNECTED) return
        val frame = CommandBuilder.subscribe(session); val cached = conversations[cacheKey(session)]
        try { if (cached != null && cached.has("checkpoint")) frame.put("resume", cached.get("checkpoint")) } catch (_: JSONException) { }
        sendFrame(ws, frame.toString())
    }
    private fun restoreAndSubscribe(session: String, key: String, generation: Int, cached: JSONObject?) {
        if (generation != subscriptionGeneration || session != desiredSessionId || key != cacheKey(session)) return
        val current = conversations[key] ?: cached
        if (current != null && session == current.optString("sessionId")) {
            conversations[key] = current
            SnapshotParser.parse(current)?.let { parsed ->
                lastSnapshot = parsed; configuration = current.optJSONObject("configuration"); configurationSessionId = session
                listener?.let { receiver ->
                    try { receiver.onTimelineMeta(JSONObject(current.toString()).put("cached", true)) } catch (_: JSONException) { }
                    configuration?.let { receiver.onConfiguration(session, it) }; receiver.onSnapshot(parsed)
                }
            }
        }
        subscriptionReady = true; sendSubscription()
    }
    fun setListener(listener: Listener?) { this.listener = listener; listener?.onConnectionState(state, stateDetail) }
    fun clearListener(listener: Listener) { if (this.listener === listener) this.listener = null }
    fun connect(baseUrl: String?, token: String?) {
        val result = EndpointPolicy.validate(baseUrl, BuildConfig.DEBUG)
        if (result != EndpointPolicy.Result.OK) { setState(ConnectionState.ERROR, describeResult(result)); return }
        val nextUrl = EndpointPolicy.normalize(baseUrl); val nextToken = token?.trim().orEmpty()
        if (this.baseUrl != nextUrl || this.token != nextToken) {
            subscriptionGeneration++; subscriptionReady = false; desiredSessionId = null
            lastSnapshot = null; configuration = null; configurationSessionId = ""
        }
        this.baseUrl = nextUrl; this.token = nextToken; authRejected = false; attempt = 0; shuttingDown = false
        cancelReconnect(); closeSocket(); setState(ConnectionState.CONNECTING, this.baseUrl); openSocket()
    }
    fun disconnect() {
        shuttingDown = true; cancelReconnect(); desiredSessionId = null; subscriptionGeneration++; subscriptionReady = false
        failAllPending("Connection closed by user"); closeSocket(); setState(ConnectionState.DISCONNECTED, "")
    }
    fun subscribe(sessionId: String) {
        desiredSessionId = sessionId; subscriptionReady = false
        val generation = ++subscriptionGeneration; val key = cacheKey(sessionId); val cached = conversations[key]; val cache = diskCache
        if (cached != null || cache == null) { restoreAndSubscribe(sessionId, key, generation, cached); return }
        cacheIo.execute { val disk = cache.read(key); main.post { restoreAndSubscribe(sessionId, key, generation, disk) } }
    }
    fun sendPrompt(sessionId: String, text: String?, images: List<ImagePayload>?, behavior: CommandBuilder.Behavior?): String? {
        val ws = socket ?: return null; if (state != ConnectionState.CONNECTED) return null
        val request = UUID.randomUUID().toString()
        val frame = try { CommandBuilder.promptCommand(sessionId, request, text, images, behavior).toString() }
            catch (_: IllegalArgumentException) { return null }
        if (!sendFrame(ws, frame)) return null
        registerPending(request, sessionId, false); return request
    }
    fun sendAbort(sessionId: String): String? {
        val ws = socket ?: return null; if (state != ConnectionState.CONNECTED) return null
        val request = UUID.randomUUID().toString()
        if (!sendFrame(ws, CommandBuilder.abortCommand(sessionId, request).toString())) return null
        registerPending(request, sessionId, true); return request
    }
    @JvmOverloads fun configure(sessionId: String, provider: String?, modelId: String?, thinkingLevel: String?, serviceTier: String? = null): String? =
        readCommand(sessionId, "configure", JSONObject().put("provider", provider).put("modelId", modelId).put("thinkingLevel", thinkingLevel).put("serviceTier", serviceTier))
    fun configuration(sessionId: String): JSONObject? = if (sessionId == configurationSessionId) configuration else null
    fun readCommand(sessionId: String, command: String, args: JSONObject): String? {
        val ws = socket ?: return null; if (state != ConnectionState.CONNECTED) return null
        val request = UUID.randomUUID().toString()
        return try {
            args.put("type", "command").put("command", command).put("sessionId", sessionId).put("requestId", request)
            if (!sendFrame(ws, args.toString())) return null
            registerPending(request, sessionId, false); request
        } catch (_: JSONException) { null }
    }
    fun state() = state
    fun stateDetail() = stateDetail
    fun catalog() = catalog
    fun lastSnapshot() = lastSnapshot
    fun baseUrl() = baseUrl
    fun api() = api
    fun http() = http
    private fun openSocket() { socket = http.newWebSocket(Request.Builder().url(EndpointPolicy.wsUrl(baseUrl)).header("Authorization", "Bearer $token").build(), this) }
    private fun closeSocket() {
        val ws = socket; socket = null
        try { ws?.close(1000, "client closing") } catch (_: RuntimeException) { }
    }
    // Check identity after posting as well: an obsolete callback must not change a replacement connection.
    private fun fromSocket(ws: WebSocket, body: () -> Unit) { runOnMain { if (ws === socket) body() } }
    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (webSocket !== socket) { webSocket.close(1000, "stale socket"); return }
        fromSocket(webSocket) { attempt = 0; setState(ConnectionState.CONNECTED, baseUrl); sendSubscription() }
    }
    override fun onMessage(webSocket: WebSocket, text: String) { fromSocket(webSocket) { handleFrame(text) } }
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) { /* JSON text only. */ }
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { if (webSocket === socket) webSocket.close(1000, null) }
    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fromSocket(webSocket) {
        socket = null; failAllPending("Connection closed before acknowledgment"); scheduleReconnect("Connection closed")
    } }
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { fromSocket(webSocket) {
        socket = null; failAllPending("Connection lost before acknowledgment")
        val code = response?.code ?: 0
        if (code == 401 || code == 403) { authRejected = true; setState(ConnectionState.ERROR, "Server rejected the token (HTTP $code). Check your token in Settings.") }
        else scheduleReconnect(t.message ?: "network error")
    } }
    private fun handleFrame(text: String) {
        if (text.isEmpty()) return
        val frame = try { JSONObject(text) } catch (_: JSONException) { notifyProtocolError("Unrecognized server frame"); return }
        val type = frame.optString("type", "")
        if (type == "snapshot" || type == "messages") {
            cacheFrame(frame)
            frame.optJSONObject("configuration")?.let { configuration = it; configurationSessionId = frame.optString("sessionId", ""); listener?.onConfiguration(configurationSessionId, it) }
            listener?.onTimelineMeta(frame)
        }
        if (type == "ack" && frame.optBoolean("ok")) frame.optJSONObject("data")?.takeIf { it.optString("type") == "history" }?.let { data ->
            val key = cacheKey(frame.optString("sessionId"))
            try { ConversationFrames.prepend(conversations[key], data)?.let { retain(key, it) } } catch (_: JSONException) { }
        }
        when (type) {
            "catalog" -> CatalogParser.parse(frame)?.let { catalog = it; listener?.onCatalog(it) }
            "snapshot" -> SnapshotParser.parse(frame)?.let { lastSnapshot = it; listener?.onSnapshot(it) }
            "messages" -> {
                val restored = if (frame.optBoolean("resumed")) conversations[cacheKey(frame.optString("sessionId"))]?.let(SnapshotParser::parse) else null
                if (restored != null) { lastSnapshot = restored; listener?.onSnapshot(restored) }
                else MessagesParser.parse(frame)?.let { listener?.onMessages(it) }
            }
            "ack" -> {
                if (frame.optBoolean("ok")) frame.optJSONObject("data")?.let { listener?.onData(frame.optString("requestId"), frame.optString("sessionId"), it) }
                AckParser.parse(frame)?.let(::resolvePending)
            }
            "error" -> notifyProtocolError(AckParser.parseErrorMessage(frame))
        }
    }
    private fun resolvePending(ack: Ack) {
        val entry = pending[ack.requestId()]
        if (entry != null && entry.sessionId == ack.sessionId()) { pending.remove(ack.requestId()); main.removeCallbacks(entry.timeout) }
        listener?.onAck(ack)
    }
    private fun registerPending(requestId: String, sessionId: String, abort: Boolean) {
        val timeout = Runnable {
            if (pending.remove(requestId) != null) listener?.onCommandUncertain(requestId, sessionId,
                "Server did not acknowledge the ${if (abort) "stop request" else "command"} within ${ACK_TIMEOUT_MS / 1000} s")
        }
        pending[requestId] = Pending(requestId, sessionId, timeout, abort); main.postDelayed(timeout, ACK_TIMEOUT_MS)
    }
    private fun failAllPending(reason: String) {
        val all = pending.values.toList(); pending.clear()
        for (entry in all) { main.removeCallbacks(entry.timeout); listener?.onCommandUncertain(entry.requestId, entry.sessionId, reason) }
    }
    private fun scheduleReconnect(reason: String) {
        if (shuttingDown || authRejected) { if (!authRejected) setState(ConnectionState.DISCONNECTED, ""); return }
        val delay = BACKOFF_MS[minOf(attempt, BACKOFF_MS.lastIndex)]; attempt++
        val effective = (delay + random.nextInt(400) - 200L).coerceAtLeast(500)
        setState(ConnectionState.RECONNECTING, "Retrying in ${Math.round(effective / 1000.0)} s" + if (reason.isEmpty()) "" else " · $reason")
        cancelReconnect()
        reconnectTask = Runnable {
            reconnectTask = null
            if (!shuttingDown && !authRejected) { setState(ConnectionState.CONNECTING, "Reconnecting to $baseUrl"); openSocket() }
        }.also { main.postDelayed(it, effective) }
    }
    private fun cancelReconnect() { reconnectTask?.let(main::removeCallbacks); reconnectTask = null }
    private fun sendFrame(ws: WebSocket, frame: String) = try { ws.send(frame) } catch (_: RuntimeException) { false }
    private fun setState(next: ConnectionState, detail: String) {
        if (state == next && stateDetail == detail) return
        state = next; stateDetail = detail; listener?.onConnectionState(next, detail)
    }
    private fun notifyProtocolError(message: String?) { if (!message.isNullOrEmpty()) listener?.onProtocolError(message) }
    private fun runOnMain(action: Runnable) { if (Looper.myLooper() == Looper.getMainLooper()) action.run() else main.post(action) }
    fun pendingRequestIds(): List<String> = ArrayList(pending.keys)
    fun shutdown() { disconnect(); cacheIo.shutdown(); pending.values.forEach { main.removeCallbacks(it.timeout) }; pending.clear() }
    companion object {
        const val ACK_TIMEOUT_MS = 20_000L
        private val BACKOFF_MS = longArrayOf(1000, 2000, 4000, 8000, 15000, 30000)
        @JvmStatic fun describeResult(result: EndpointPolicy.Result) = when (result) {
            EndpointPolicy.Result.EMPTY -> "Enter a server URL."
            EndpointPolicy.Result.MALFORMED -> "Invalid server URL."
            EndpointPolicy.Result.UNSUPPORTED_SCHEME -> "Only HTTPS and HTTP (debug) are supported."
            EndpointPolicy.Result.CLEARTEXT_NOT_ALLOWED -> "HTTP is allowed only for 10.0.2.2 and localhost in debug builds. Use HTTPS."
            EndpointPolicy.Result.HAS_CREDENTIALS -> "Remove credentials from the URL; enter your token separately."
            EndpointPolicy.Result.HAS_QUERY_OR_FRAGMENT -> "URL must not contain a query or fragment."
            EndpointPolicy.Result.MISSING_HOST -> "URL is missing a host."
            else -> "URL rejected."
        }
    }
}
