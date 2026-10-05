package ru.billyhargrove.pimobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextSanitizerTest {

    @Test
    public void keepsNormalTextAndNewlines() {
        assertEquals("привет\nмир", TextSanitizer.safe("привет\nмир"));
        assertEquals("a\tb", TextSanitizer.safe("a\tb"));
    }

    @Test
    public void dropsControlCharacters() {
        assertEquals("abc", TextSanitizer.safe("a\u0000b\u0007c\u007F"));
        assertEquals("ab", TextSanitizer.safe("a\u001Bb"));
    }

    @Test
    public void normalisesWindowsNewlinesAndTrailingWhitespace() {
        assertEquals("a\nb", TextSanitizer.safe("a\r\nb\r\n"));
        assertEquals("a\nb", TextSanitizer.safe("a\nb   \n"));
    }

    @Test
    public void collapsesRunawayBlankLines() {
        // At most three consecutive newlines are kept (MAX_CONSECUTIVE_NEWLINES = 3).
        assertEquals("a\n\n\nb", TextSanitizer.safe("a\n\n\n\n\n\n\nb"));
        assertEquals("a\n\n\nb", TextSanitizer.safe("a\n\n\nb"));
    }

    @Test
    public void handlesUnpairedSurrogates() {
        assertEquals("a\uFFFDb", TextSanitizer.safe("a\uD83Db"));
        assertEquals("a\uD83D\uDE00b", TextSanitizer.safe("a\uD83D\uDE00b"));
        assertEquals("\uFFFD", TextSanitizer.safe("\uDE00"));
    }

    @Test
    public void handlesNullAndEmpty() {
        assertEquals("", TextSanitizer.safe(null));
        assertEquals("", TextSanitizer.safe(""));
        assertEquals("", TextSanitizer.safe("\n\n\n"));
        assertEquals("", TextSanitizer.safe("   \n  "));
    }

    @Test
    public void markupIsNotInterpreted() {
        String raw = "<b>жирный</b> & <script>alert(1)</script>";
        assertEquals(raw, TextSanitizer.safe(raw));
        assertTrue(TextSanitizer.safe(raw).contains("<script>"));
    }
}
