package ru.billyhargrove.pimobile.store;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.Nullable;

import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import ru.billyhargrove.pimobile.core.PayloadCodec;

/**
 * Encrypts the gateway token with an AES-256-GCM key that lives in the Android
 * Keystore and never leaves it.
 *
 * <ul>
 *   <li>the key is generated on first use with {@code setRandomizedEncryptionRequired(true)},
 *       so the platform refuses to reuse an IV;</li>
 *   <li>the IV is stored next to the ciphertext (Base64 of {@code iv || ciphertext});</li>
 *   <li>the token itself is never written to logs, never put into a URL and never
 *       copied into a backup (backups are disabled in the manifest).</li>
 * </ul>
 */
public final class SecureTokenStore {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pimobile.gateway.token.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    /** @return Base64 of iv+ciphertext, or {@code null} when the input is empty. */
    @Nullable
    public String encrypt(@Nullable String plaintext) throws GeneralSecurityException {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return PayloadCodec.pack(cipher.getIV(), ciphertext);
    }

    /**
     * @return the plaintext token, or {@code null} when the payload is missing or
     *         cannot be decrypted (for example the keystore key was invalidated).
     */
    @Nullable
    public String decrypt(@Nullable String packed) {
        if (packed == null || packed.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = PayloadCodec.iv(packed);
            byte[] ciphertext = PayloadCodec.ciphertext(packed);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    /** Drops the keystore entry; the next encrypt() creates a fresh key. */
    public void reset() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS);
            }
        } catch (Exception ignored) {
            // Nothing we can do; the next write will try to generate the key again.
        }
    }

    private SecretKey secretKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        try {
            keyStore.load(null);
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException("Не удалось открыть AndroidKeyStore", e);
        }
        java.security.Key existing = null;
        try {
            existing = keyStore.getKey(KEY_ALIAS, null);
        } catch (GeneralSecurityException ignored) {
            // fall through to re-generate
        }
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
