package ru.billyhargrove.pimobile.core

/** Pure AES-GCM envelope, independent of the Android keystore. */
object PayloadCodec {
    const val IV_LENGTH = 12
    @JvmStatic fun pack(iv: ByteArray?, ciphertext: ByteArray?): String {
        require(iv != null && iv.size == IV_LENGTH) { "IV должен быть длиной $IV_LENGTH байт" }
        require(ciphertext != null && ciphertext.isNotEmpty()) { "Пустой шифротекст" }
        return java.util.Base64.getEncoder().encodeToString(iv + ciphertext)
    }
    @JvmStatic fun iv(packed: String?): ByteArray = unpack(packed).copyOfRange(0, IV_LENGTH)
    @JvmStatic fun ciphertext(packed: String?): ByteArray = unpack(packed).let { it.copyOfRange(IV_LENGTH, it.size) }
    private fun unpack(packed: String?): ByteArray {
        require(!packed.isNullOrEmpty()) { "Пустые данные" }
        val raw = try { java.util.Base64.getDecoder().decode(packed) }
            catch (error: IllegalArgumentException) { throw IllegalArgumentException("Повреждённые данные", error) }
        require(raw.size > IV_LENGTH) { "Повреждённые данные" }
        return raw
    }
}
