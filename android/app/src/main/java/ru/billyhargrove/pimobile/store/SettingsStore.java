package ru.billyhargrove.pimobile.store;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import ru.billyhargrove.pimobile.core.CommandBuilder;
import ru.billyhargrove.pimobile.core.EndpointPolicy;

/**
 * Persistence for the connect form.
 *
 * <p>The base URL is stored as plain text (it is not a secret); the bearer token
 * is stored only in its AES-GCM encrypted form. Nothing else – no chat content,
 * no message ids – is written to disk on purpose.</p>
 */
public final class SettingsStore {

    private static final String PREFS = "pi_mobile_settings";
    private static final String KEY_BASE_URL = "base_url";
    private static final String KEY_TOKEN = "token_gcm";
    private static final String KEY_BEHAVIOR = "last_behavior";

    private final SharedPreferences prefs;
    private final SecureTokenStore tokenStore;

    public SettingsStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.tokenStore = new SecureTokenStore();
    }

    public String baseUrl() {
        String value = prefs.getString(KEY_BASE_URL, null);
        if (value == null || value.trim().isEmpty()) {
            return EndpointPolicy.DEFAULT_BASE_URL;
        }
        return value;
    }

    public void setBaseUrl(String baseUrl) {
        prefs.edit().putString(KEY_BASE_URL, baseUrl == null ? "" : baseUrl.trim()).apply();
    }

    /** @return the decrypted token, or "" when none is stored / it cannot be read. */
    public String token() {
        String packed = prefs.getString(KEY_TOKEN, null);
        if (packed == null || packed.isEmpty()) {
            return "";
        }
        String plain = tokenStore.decrypt(packed);
        if (plain == null) {
            // Keystore key was lost (restore, OS reset): forget the unusable blob.
            prefs.edit().remove(KEY_TOKEN).apply();
            return "";
        }
        return plain;
    }

    public void setToken(@Nullable String token) {
        String trimmed = token == null ? "" : token.trim();
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_TOKEN).apply();
            return;
        }
        try {
            String packed = tokenStore.encrypt(trimmed);
            if (packed == null) {
                prefs.edit().remove(KEY_TOKEN).apply();
            } else {
                prefs.edit().putString(KEY_TOKEN, packed).apply();
            }
        } catch (Exception e) {
            // Never fall back to storing the plaintext token.
            prefs.edit().remove(KEY_TOKEN).apply();
        }
    }

    public boolean hasToken() {
        String packed = prefs.getString(KEY_TOKEN, null);
        return packed != null && !packed.isEmpty();
    }

    public boolean hasConnection() {
        return hasToken() && !baseUrl().isEmpty();
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
        tokenStore.reset();
    }

    public CommandBuilder.Behavior behavior() {
        String raw = prefs.getString(KEY_BEHAVIOR, CommandBuilder.Behavior.FOLLOW_UP.wire());
        return CommandBuilder.Behavior.STEER.wire().equals(raw)
                ? CommandBuilder.Behavior.STEER
                : CommandBuilder.Behavior.FOLLOW_UP;
    }

    public void setBehavior(CommandBuilder.Behavior behavior) {
        prefs.edit().putString(KEY_BEHAVIOR, (behavior == null ? CommandBuilder.Behavior.FOLLOW_UP : behavior).wire())
                .apply();
    }
}
