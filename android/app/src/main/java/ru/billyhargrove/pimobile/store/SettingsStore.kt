package ru.billyhargrove.pimobile.store

import android.content.Context
import ru.billyhargrove.pimobile.core.CommandBuilder
import ru.billyhargrove.pimobile.core.EndpointPolicy

/** Preserve the existing preference keys and encrypted token envelope. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pi_mobile_settings", Context.MODE_PRIVATE)
    private val tokenStore = SecureTokenStore()
    fun baseUrl(): String = prefs.getString("base_url", null)?.takeUnless(String::isBlank) ?: EndpointPolicy.DEFAULT_BASE_URL
    fun setBaseUrl(baseUrl: String?) { prefs.edit().putString("base_url", baseUrl?.trim().orEmpty()).apply() }
    fun token(): String {
        val packed = prefs.getString("token_gcm", null)?.takeIf(String::isNotEmpty) ?: return ""
        val plain = tokenStore.decrypt(packed)
        if (plain == null) prefs.edit().remove("token_gcm").apply()
        return plain.orEmpty()
    }
    fun setToken(token: String?) {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isEmpty()) { prefs.edit().remove("token_gcm").apply(); return }
        try {
            val packed = tokenStore.encrypt(trimmed)
            if (packed == null) throw IllegalStateException("Could not encrypt access token")
            prefs.edit().putString("token_gcm", packed).apply()
        } catch (cause: Exception) { throw IllegalStateException("Could not save access token securely", cause) }
    }
    fun hasToken() = !prefs.getString("token_gcm", null).isNullOrEmpty()
    fun hasConnection() = hasToken() && baseUrl().isNotEmpty()
    fun clearToken() { prefs.edit().remove("token_gcm").apply(); tokenStore.reset() }
    fun behavior() = if (prefs.getString("last_behavior", CommandBuilder.Behavior.FOLLOW_UP.wire()) == CommandBuilder.Behavior.STEER.wire())
        CommandBuilder.Behavior.STEER else CommandBuilder.Behavior.FOLLOW_UP
    fun setBehavior(behavior: CommandBuilder.Behavior?) { prefs.edit().putString("last_behavior", (behavior ?: CommandBuilder.Behavior.FOLLOW_UP).wire()).apply() }
}
