package ru.billyhargrove.pimobile.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import ru.billyhargrove.pimobile.core.PayloadCodec

/** Existing AES256-GCM key stays inside AndroidKeyStore; never fall back to plaintext. */
class SecureTokenStore {
    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "pimobile.gateway.token.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: String?): String? {
        if (plaintext.isNullOrEmpty()) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return PayloadCodec.pack(cipher.iv, cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(packed: String?): String? {
        if (packed.isNullOrEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, PayloadCodec.iv(packed)))
            String(cipher.doFinal(PayloadCodec.ciphertext(packed)), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) { null } catch (_: RuntimeException) { null }
    }
    fun reset() {
        try { val store = KeyStore.getInstance(KEYSTORE); store.load(null); if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS) }
        catch (_: Exception) { /* A subsequent write can regenerate the key. */ }
    }
    @Throws(GeneralSecurityException::class)
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE)
        try { store.load(null) } catch (cause: java.io.IOException) { throw GeneralSecurityException("Не удалось открыть AndroidKeyStore", cause) }
        val existing = try { store.getKey(ALIAS, null) } catch (_: GeneralSecurityException) { null }
        if (existing is SecretKey) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
