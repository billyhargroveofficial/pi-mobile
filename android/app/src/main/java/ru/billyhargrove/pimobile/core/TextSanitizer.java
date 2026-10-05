package ru.billyhargrove.pimobile.core;

/**
 * Text normalisation before it reaches a {@code TextView}.
 *
 * <p>The protocol says "safe text rendering": the app never parses HTML from the
 * server and never enables {@code Html.fromHtml}. This helper additionally drops
 * control characters (keeping newlines and tabs), collapses runaway blank lines
 * and replaces unpaired surrogates so that no message can break the layout.</p>
 */
public final class TextSanitizer {

    private static final int MAX_CONSECUTIVE_NEWLINES = 3;

    private TextSanitizer() {
    }

    public static String safe(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        int consecutiveNewlines = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < raw.length() && Character.isLowSurrogate(raw.charAt(i + 1))) {
                    out.append(c).append(raw.charAt(i + 1));
                    i++;
                    continue;
                }
                out.append('\uFFFD');
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                out.append('\uFFFD');
                continue;
            }
            if (c == '\n') {
                consecutiveNewlines++;
                if (consecutiveNewlines > MAX_CONSECUTIVE_NEWLINES) {
                    continue;
                }
                out.append(c);
                continue;
            }
            consecutiveNewlines = 0;
            if (c == '\t') {
                out.append(c);
                continue;
            }
            if (c < 0x20 || c == 0x7F) {
                continue;
            }
            out.append(c);
        }
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == '\n' || out.charAt(end - 1) == ' ')) {
            end--;
        }
        return out.substring(0, end);
    }
}
