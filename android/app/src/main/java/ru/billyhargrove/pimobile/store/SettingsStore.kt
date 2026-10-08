package ru.billyhargrove.pimobile.store

import android.content.Context
import android.content.SharedPreferences
import ru.billyhargrove.pimobile.BuildConfig
import ru.billyhargrove.pimobile.core.CommandBuilder
import ru.billyhargrove.pimobile.core.EndpointPolicy

/** Preserve legacy active keys/GCM envelopes; profiles add atomic, individually encrypted credentials. */
class SettingsStore internal constructor(private val prefs: SharedPreferences, private val tokenStore: SecureTokenStore) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("pi_mobile_settings", Context.MODE_PRIVATE), SecureTokenStore())
    private val computers = ConnectionProfiles(object : ConnectionProfiles.Port {
        override fun read() = ConnectionProfiles.Raw(prefs.getString("connection_profiles_v1", null),
            prefs.getString("base_url", null)?.takeUnless(String::isBlank) ?: EndpointPolicy.DEFAULT_BASE_URL,
            prefs.getString("token_gcm", null)?.takeIf(String::isNotEmpty))
        override fun write(raw: ConnectionProfiles.Raw) {
            val before = read()
            fun commit(value: ConnectionProfiles.Raw) = prefs.edit().apply {
                putString("connection_profiles_v1", value.profiles); putString("base_url", value.baseUrl)
                if (value.envelope == null) remove("token_gcm") else putString("token_gcm", value.envelope)
            }.commit()
            if (!commit(raw)) {
                // SharedPreferences can update memory despite a failed disk commit.
                // Restore the previous visible state and never start a socket for the failed save.
                commit(before)
                error("Could not save computer settings")
            }
        }
        override fun encrypt(token: String) = tokenStore.encrypt(token)
        override fun decrypt(envelope: String) = tokenStore.decrypt(envelope)
    }, BuildConfig.DEBUG)
    fun baseUrl() = computers.baseUrl()
    fun token() = computers.token()
    fun hasToken() = computers.hasToken()
    fun hasTokenFor(url: String) = computers.hasTokenFor(url)
    fun hasConnection() = hasToken() && baseUrl().isNotEmpty()
    fun profiles() = computers.profiles()
    fun activeProfileId() = computers.activeId()
    fun saveComputer(url: String, name: String, token: String) = computers.saveAndSelect(url, name, token)
    fun selectComputer(id: String) = computers.select(id)
    fun forgetComputer(id: String) = computers.forget(id)
    private var scopeSource: Triple<String, String?, String?>? = null
    private var scope = ""
    /** Opaque cached identity: streaming guards do not decrypt a token on every frame. */
    fun connectionScope(): String {
        val base = baseUrl()
        fun source() = Triple(base, prefs.getString("token_gcm", null), prefs.getString("connection_profiles_v1", null))
        val next = source()
        if (next == scopeSource) return scope
        val normalized = try { EndpointPolicy.normalize(base) } catch (_: Exception) { base }
        scope = ConversationCache.key(normalized, token(), "connection")
        scopeSource = source() // A failed decryption may have retired just this envelope.
        return scope
    }
    // Compatible setup API. UI uses saveComputer rather than a non-atomic URL/token pair.
    fun setBaseUrl(baseUrl: String?) = computers.setBaseUrl(baseUrl?.trim()?.takeIf(String::isNotEmpty) ?: EndpointPolicy.DEFAULT_BASE_URL)
    fun setToken(token: String?) {
        val typed = token?.trim().orEmpty()
        if (typed.isEmpty()) clearToken()
        else computers.saveAndSelect(baseUrl(), profiles().find { it.id == activeProfileId() }?.name.orEmpty(), typed)
    }
    /** Never delete the shared Keystore alias: that would invalidate the other computers. */
    fun clearToken() = computers.clearToken()
    fun behavior() = if (prefs.getString("last_behavior", CommandBuilder.Behavior.FOLLOW_UP.wire()) == CommandBuilder.Behavior.STEER.wire())
        CommandBuilder.Behavior.STEER else CommandBuilder.Behavior.FOLLOW_UP
    fun setBehavior(behavior: CommandBuilder.Behavior?) { prefs.edit().putString("last_behavior", (behavior ?: CommandBuilder.Behavior.FOLLOW_UP).wire()).apply() }
}
