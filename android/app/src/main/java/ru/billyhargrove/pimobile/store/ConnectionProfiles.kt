package ru.billyhargrove.pimobile.store

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import ru.billyhargrove.pimobile.core.ConnectionProfile
import ru.billyhargrove.pimobile.core.EndpointPolicy

/** Synchronous, atomic profile transactions. Only the injected cipher sees plaintext tokens. */
internal class ConnectionProfiles(private val port: Port, private val debug: Boolean, private val newId: () -> String = { UUID.randomUUID().toString() }) {
    interface Port {
        fun read(): Raw
        fun write(raw: Raw)
        fun encrypt(token: String): String?
        fun decrypt(envelope: String): String?
    }
    data class Raw(val profiles: String?, val baseUrl: String, val envelope: String?)
    private data class Saved(val id: String, val name: String, val url: String, val envelope: String?) {
        fun metadata() = ConnectionProfile(id, name, url, !envelope.isNullOrEmpty())
    }
    private data class State(val entries: List<Saved>, val active: String?, val readable: Boolean = true)
    private var cachedRaw: Raw? = null
    private var cachedState = State(emptyList(), null)
    companion object { const val MAX_PROFILES = 32 }

    private fun state(): State {
        val raw = port.read()
        if (raw == cachedRaw) return cachedState
        val next = if (raw.profiles == null) {
            // Lazy migration: keep the original URL/outbox namespace and GCM bytes.
            // Merely opening the upgraded app does not rewrite the legacy preferences.
            if (!raw.envelope.isNullOrEmpty() && EndpointPolicy.validate(raw.baseUrl, debug) == EndpointPolicy.Result.OK)
                State(listOf(Saved("legacy", label(raw.baseUrl), raw.baseUrl, raw.envelope)), "legacy")
            else State(emptyList(), null)
        } else try {
            require(raw.profiles.length <= 2 * 1024 * 1024)
            val json = JSONObject(raw.profiles)
            require(json.getInt("version") == 1)
            val array = json.getJSONArray("computers")
            require(array.length() <= MAX_PROFILES)
            val ids = hashSetOf<String>(); val urls = hashSetOf<String>()
            val entries = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val id = item.getString("id"); val url = item.getString("url"); val name = item.getString("name")
                require(id.isNotBlank() && id.length <= 100 && ids.add(id))
                require(name.isNotBlank() && name.length <= 80)
                require(url.length <= 4096 && EndpointPolicy.validate(url, debug) == EndpointPolicy.Result.OK && urls.add(EndpointPolicy.normalize(url)))
                val envelope = item.optString("token_gcm").takeIf(String::isNotEmpty)
                require(envelope == null || envelope.length <= 65536)
                Saved(id, name, url, envelope)
            }
            val active = json.optString("activeId").takeIf(String::isNotEmpty)
            require(active == null || active in ids)
            State(entries, active)
        } catch (_: Exception) {
            // Do not overwrite corrupted/future metadata or borrow its legacy mirror token.
            State(emptyList(), null, false)
        }
        cachedRaw = raw; cachedState = next
        return next
    }
    fun profiles(): List<ConnectionProfile> = java.util.Collections.unmodifiableList(state().entries.map(Saved::metadata))
    fun activeId(): String? = state().active
    fun baseUrl(): String = state().let { current -> current.entries.find { it.id == current.active }?.url ?: port.read().baseUrl }
    fun hasToken(): Boolean = state().let { current -> current.entries.find { it.id == current.active }?.envelope?.isNotEmpty() == true }
    fun hasTokenFor(url: String): Boolean = find(state(), url)?.envelope?.isNotEmpty() == true
    fun token(): String {
        val current = state(); val active = current.entries.find { it.id == current.active } ?: return ""
        return decrypt(current, active).orEmpty()
    }
    fun saveAndSelect(url: String, name: String, typedToken: String): ConnectionProfile {
        val current = writable()
        require(url.length <= 4096 && EndpointPolicy.validate(url, debug) == EndpointPolicy.Result.OK) { "Invalid computer URL (maximum 4096 characters)" }
        val prior = find(current, url)
        val displayName = name.trim().ifEmpty { prior?.name ?: label(url) }
        require(displayName.length <= 80) { "Computer name must be at most 80 characters" }
        val typed = typedToken.trim()
        require(typed.length <= 16384) { "Access token is too long" }
        val envelope = if (typed.isNotEmpty()) {
            try { port.encrypt(typed)?.takeIf { it.isNotEmpty() && it.length <= 65536 } ?: error("Encryption failed") }
            catch (_: Exception) { throw IllegalStateException("Could not save access token securely") }
        } else {
            require(prior != null && !prior.envelope.isNullOrEmpty()) { "Enter an access token for this computer" }
            if (decrypt(current, prior).isNullOrEmpty()) error("Saved token is unavailable. Enter it again for this computer")
            prior.envelope
        }
        if (prior == null) require(current.entries.size < MAX_PROFILES) { "At most $MAX_PROFILES computers can be saved" }
        val id = prior?.id ?: newId().also { require(it.isNotBlank() && it.length <= 100 && current.entries.none { entry -> entry.id == it }) }
        // Keep a migrated endpoint's original spelling so its existing receipts survive.
        val saved = Saved(id, displayName, prior?.url ?: EndpointPolicy.normalize(url), envelope)
        val entries = if (prior == null) current.entries + saved else current.entries.map { if (it.id == id) saved else it }
        persist(State(entries, id)); return saved.metadata()
    }
    fun select(id: String): ConnectionProfile {
        val current = writable(); val saved = current.entries.find { it.id == id } ?: error("Computer no longer exists")
        if (saved.envelope.isNullOrEmpty() || decrypt(current, saved).isNullOrEmpty())
            error("Saved token is unavailable. Edit this computer and enter its token")
        persist(current.copy(active = id)); return saved.metadata()
    }
    fun forget(id: String) {
        val current = writable()
        if (current.entries.none { it.id == id }) return
        persist(State(current.entries.filterNot { it.id == id }, current.active.takeUnless { it == id }))
    }
    fun clearToken() {
        val current = writable()
        persist(current.copy(entries = current.entries.map { if (it.id == current.active) it.copy(envelope = null) else it }), baseUrl())
    }
    /** Legacy setup API: changing address never carries a previous computer's credential. */
    fun setBaseUrl(url: String) {
        val current = writable()
        require(EndpointPolicy.validate(url, debug) == EndpointPolicy.Result.OK) { "Invalid computer URL" }
        val saved = find(current, url)
        persist(current.copy(active = saved?.id), saved?.url ?: url.trim())
    }
    private fun decrypt(current: State, saved: Saved): String? {
        val envelope = saved.envelope ?: return null
        val token = try { port.decrypt(envelope)?.takeIf(String::isNotEmpty) } catch (_: Exception) { null }
        if (token == null) persist(current.copy(entries = current.entries.map { if (it.id == saved.id) it.copy(envelope = null) else it }), baseUrl())
        return token
    }
    private fun writable(): State = state().also { check(it.readable) { "Saved computers could not be read. No stored data has been replaced" } }
    private fun find(current: State, url: String): Saved? {
        val normalized = try { EndpointPolicy.normalize(url) } catch (_: Exception) { return null }
        return current.entries.find { EndpointPolicy.normalize(it.url) == normalized }
    }
    private fun label(url: String) = try {
        java.net.URI(EndpointPolicy.normalize(url)).let { it.host + if (it.port >= 0) ":${it.port}" else "" }.take(80)
    } catch (_: Exception) { "Computer" }
    private fun persist(next: State, fallbackUrl: String = EndpointPolicy.DEFAULT_BASE_URL) {
        val active = next.entries.find { it.id == next.active }
        val json = JSONObject().put("version", 1).put("activeId", next.active.orEmpty()).put("computers", JSONArray().apply {
            next.entries.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).apply {
                it.envelope?.let { envelope -> put("token_gcm", envelope) }
            }) }
        }).toString()
        check(json.length <= 2 * 1024 * 1024) { "Saved computers exceed the storage limit" }
        val raw = Raw(json, active?.url ?: fallbackUrl, active?.envelope)
        port.write(raw) // Reservation publishes only after durable commit succeeds.
        cachedRaw = raw; cachedState = next
    }
}
