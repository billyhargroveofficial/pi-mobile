package ru.billyhargrove.pimobile.core;

/**
 * Packs an AES-GCM payload as {@code base64(iv || ciphertext)}.
 *
 * <p>Kept as a pure-Java helper (no AndroidKeyStore) so that the packing logic
 * can be unit tested on the JVM; the keystore itself lives in
 * {@code store.SecureTokenStore}.</p>
 */
public final class PayloadCodec {

    /** AES-GCM with a 96-bit IV is the AndroidKeyStore default and is what we use. */
    public static final int IV_LENGTH = 12;

    private PayloadCodec() {
    }

    public static String pack(byte[] iv, byte[] ciphertext) {
        if (iv == null || iv.length != IV_LENGTH) {
            throw new IllegalArgumentException("IV должен быть длиной " + IV_LENGTH + " байт");
        }
        if (ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("Пустой шифротекст");
        }
        byte[] out = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
        return java.util.Base64.getEncoder().encodeToString(out);
    }

    public static byte[] iv(String packed) {
        return unpack(packed)[0];
    }

    public static byte[] ciphertext(String packed) {
        return unpack(packed)[1];
    }

    private static byte[][] unpack(String packed) {
        if (packed == null || packed.isEmpty()) {
            throw new IllegalArgumentException("Пустые данные");
        }
        byte[] raw;
        try {
            raw = java.util.Base64.getDecoder().decode(packed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Повреждённые данные", e);
        }
        if (raw.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Повреждённые данные");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[raw.length - IV_LENGTH];
        System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
        System.arraycopy(raw, IV_LENGTH, ciphertext, 0, ciphertext.length);
        return new byte[][]{iv, ciphertext};
    }
}
