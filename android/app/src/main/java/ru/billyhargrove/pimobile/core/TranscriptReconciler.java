package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges a server snapshot with the locally authored messages the user typed but
 * which may not have reached the transcript yet.
 *
 * <p>A snapshot (or the canonical transcript built from snapshots plus
 * {@code messages} frames) always replaces the transcript, so optimistic bubbles
 * would be wiped on every update. Instead of keeping a separate shadow list, the adapter
 * asks this class for the effective transcript:</p>
 * <ul>
 *   <li>a local bubble whose text is already present in the snapshot is dropped –
 *       the server copy is authoritative;</li>
 *   <li>a local bubble that the gateway rejected ({@code FAILED}) is always kept
 *       so the user sees the error and can restore the composer content;</li>
 *   <li>a local bubble that is still unconfirmed stays visible until it shows up
 *       in a snapshot.</li>
 * </ul>
 */
public final class TranscriptReconciler {

    private TranscriptReconciler() {
    }

    public static List<ChatMessage> merge(List<ChatMessage> baseMessages, List<ChatMessage> localMessages) {
        List<ChatMessage> result = new ArrayList<>();
        if (baseMessages != null) {
            result.addAll(baseMessages);
        }
        if (localMessages == null || localMessages.isEmpty()) {
            return result;
        }

        Map<String, Integer> availableUserTexts = new HashMap<>();
        if (baseMessages != null) {
            for (ChatMessage message : baseMessages) {
                if (message.role() == ChatMessage.Role.USER) {
                    String key = key(message);
                    Integer current = availableUserTexts.get(key);
                    availableUserTexts.put(key, current == null ? 1 : current + 1);
                }
            }
        }

        for (ChatMessage local : localMessages) {
            if (local.localState() == ChatMessage.LocalState.FAILED) {
                result.add(local);
                continue;
            }
            String key = key(local);
            Integer current = availableUserTexts.get(key);
            if (current != null && current > 0) {
                availableUserTexts.put(key, current - 1);
                continue; // server echo wins
            }
            result.add(local);
        }
        return result;
    }

    /** True when a message must never be replayed automatically. */
    public static boolean isUncertain(ChatMessage message) {
        return message != null
                && (message.localState() == ChatMessage.LocalState.UNCERTAIN
                || message.localState() == ChatMessage.LocalState.SENDING);
    }

    private static String key(ChatMessage message) {
        String text = message.text();
        // Pi's image resizing appends this exact metadata to the user text.
        // Only normalize image messages; ordinary quoted text must stay untouched.
        if (message.hasImages()) {
            text = text.replaceAll("(?:\\s*\\[Image: original \\d+x\\d+, displayed at \\d+x\\d+\\. Multiply coordinates by [0-9.]+ to map to original image\\.\\])+$", "").replaceAll("\\s+$", "");
            if (text.equals("Посмотри изображение")) text = "";
        }
        return message.images().size() + ":" + text;
    }
}
