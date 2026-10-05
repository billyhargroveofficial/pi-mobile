package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Base64;

public class PayloadCodecTest {

    @Test
    public void packsIvAndCiphertextWithoutLoss() {
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[]{9, 8, 7, 6, 5};
        for (int i = 0; i < iv.length; i++) {
            iv[i] = (byte) i;
        }
        String packed = PayloadCodec.pack(iv, ciphertext);
        assertArrayEquals(iv, PayloadCodec.iv(packed));
        assertArrayEquals(ciphertext, PayloadCodec.ciphertext(packed));
        assertEquals(iv.length + ciphertext.length,
                Base64.getDecoder().decode(packed).length);
    }

    @Test
    public void packedValueIsNotThePlaintext() {
        byte[] iv = new byte[12];
        byte[] secret = "super-secret-token".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String packed = PayloadCodec.pack(iv, secret);
        assertFalse(packed.contains("super-secret-token"));
        assertTrue(packed.length() > 10);
    }

    @Test
    public void rejectsBadIvLengths() {
        try {
            PayloadCodec.pack(new byte[8], new byte[]{1});
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("IV"));
        }
        try {
            PayloadCodec.pack(null, new byte[]{1});
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertEquals(true, expected.getMessage().contains("IV"));
        }
    }

    @Test
    public void rejectsEmptyCiphertext() {
        try {
            PayloadCodec.pack(new byte[12], new byte[0]);
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("шифротекст"));
        }
    }

    @Test
    public void rejectsCorruptPayloads() {
        try {
            PayloadCodec.iv("не-base64!!");
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertEquals(true, expected.getMessage().contains("Повреждённые"));
        }
        try {
            PayloadCodec.iv(Base64.getEncoder().encodeToString(new byte[5]));
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertEquals(true, expected.getMessage().contains("Повреждённые"));
        }
        try {
            PayloadCodec.iv("");
            fail("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertEquals(true, expected.getMessage().contains("Пустые"));
        }
    }
}
