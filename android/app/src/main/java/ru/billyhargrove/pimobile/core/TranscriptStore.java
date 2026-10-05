package ru.billyhargrove.pimobile.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordered canonical transcript, keyed by message id.
 *
 * <p>The gateway sends a full snapshot on subscribe/reconnect and then only new or
 * changed messages, so the client has to keep the authoritative order itself:</p>
 * <ul>
 *   <li>{@link #replaceAll(List)} – snapshot: clears and rebuilds the order;</li>
 *   <li>{@link #apply(List, List)} – incremental frame: a message with a known id
 *       is <em>updated in place</em> (its position never jumps), new messages are
 *       appended, {@code removedIds} are dropped.</li>
 * </ul>
 *
 * <p>Messages without an id fall back to role+text as their key; they cannot be
 * updated by the gateway and are therefore always treated as immutable entries.</p>
 */
public final class TranscriptStore {

    /** What an incremental update actually changed. */
    public static final class ChangeSet {
        private final List<ChatMessage> transcript;
        private final int added;
        private final int updated;
        private final int removed;
        private final boolean tailTouched;

        ChangeSet(List<ChatMessage> transcript, int added, int updated, int removed, boolean tailTouched) {
            this.transcript = transcript;
            this.added = added;
            this.updated = updated;
            this.removed = removed;
            this.tailTouched = tailTouched;
        }

        public List<ChatMessage> transcript() {
            return transcript;
        }

        public int added() {
            return added;
        }

        public int updated() {
            return updated;
        }

        public int removed() {
            return removed;
        }

        /** True when the update only appended at the end (cheapest case to render). */
        public boolean appendedOnly() {
            return added > 0 && updated == 0 && removed == 0;
        }

        /** True when the last message of the transcript changed or appeared. */
        public boolean tailTouched() {
            return tailTouched;
        }

        public boolean isEmpty() {
            return added == 0 && updated == 0 && removed == 0;
        }
    }

    private static final class Entry {
        ChatMessage message;

        Entry(ChatMessage message) {
            this.message = message;
        }
    }

    private final LinkedHashMap<String, Entry> byKey = new LinkedHashMap<>();
    private final List<ChatMessage> snapshotView = new ArrayList<>();
    private long revision;
    private int anonymousSequence;

    /** Replaces the whole transcript with a snapshot (order = snapshot order). */
    public ChangeSet replaceAll(List<ChatMessage> messages) {
        int oldSize = byKey.size();
        byKey.clear();
        snapshotView.clear();
        anonymousSequence = 0;
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message == null) {
                    continue;
                }
                byKey.put(uniqueKey(message, true), new Entry(message));
            }
        }
        revision++;
        rebuildView();
        return new ChangeSet(new ArrayList<>(snapshotView), snapshotView.size(), 0, oldSize, snapshotView.size() > 0);
    }

    /** Applies an incremental frame: changed messages by id, new ones appended. */
    public ChangeSet apply(List<ChatMessage> changed, List<String> removedIds) {
        int added = 0;
        int updated = 0;
        int removed = 0;
        Set<String> touchedKeys = new HashSet<>();

        if (removedIds != null) {
            for (String id : removedIds) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                if (byKey.remove(id) != null) {
                    removed++;
                }
            }
        }

        if (changed != null) {
            for (ChatMessage message : changed) {
                if (message == null) {
                    continue;
                }
                if (!message.id().isEmpty()) {
                    Entry existing = byKey.get(message.id());
                    if (existing != null) {
                        if (!existing.message.equals(message)) {
                            // A changed message keeps its position; only the tail is
                            // allowed to trigger an auto-scroll.
                            updated++;
                            existing.message = message;
                            touchedKeys.add(message.id());
                        }
                    } else {
                        byKey.put(message.id(), new Entry(message));
                        added++;
                        touchedKeys.add(message.id());
                    }
                } else {
                    String key = message.stableKey();
                    Entry existing = byKey.get(key);
                    if (existing != null && existing.message.id().isEmpty()) {
                        if (!existing.message.equals(message)) {
                            updated++;
                            existing.message = message;
                            touchedKeys.add(key);
                        }
                    } else {
                        String unique = uniqueKey(message, true);
                        byKey.put(unique, new Entry(message));
                        added++;
                        touchedKeys.add(unique);
                    }
                }
            }
        }

        if (added > 0 || updated > 0 || removed > 0) {
            revision++;
        }
        rebuildView();
        boolean tailTouched = false;
        String lastKey = lastKey();
        if (lastKey != null && touchedKeys.contains(lastKey)) {
            tailTouched = true;
        }
        return new ChangeSet(new ArrayList<>(snapshotView), added, updated, removed, tailTouched);
    }

    public void prepend(List<ChatMessage> older) {
        LinkedHashMap<String,Entry> merged=new LinkedHashMap<>();
        for(ChatMessage m:older)if(m!=null)merged.put(m.stableKey(),new Entry(m));
        merged.putAll(byKey);byKey.clear();byKey.putAll(merged);revision++;rebuildView();
    }

    public List<ChatMessage> transcript() {
        return new ArrayList<>(snapshotView);
    }

    public int size() {
        return snapshotView.size();
    }

    public boolean isEmpty() {
        return snapshotView.isEmpty();
    }

    public long revision() {
        return revision;
    }

    public ChatMessage byId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        Entry entry = byKey.get(id);
        return entry == null ? null : entry.message;
    }

    public void clear() {
        byKey.clear();
        snapshotView.clear();
        anonymousSequence = 0;
        revision++;
    }

    private void rebuildView() {
        snapshotView.clear();
        for (Entry entry : byKey.values()) {
            snapshotView.add(entry.message);
        }
    }

    private String lastKey() {
        String last = null;
        for (String key : byKey.keySet()) {
            last = key;
        }
        return last;
    }

    /** Keys are ids when present; otherwise role+text with a disambiguating suffix. */
    private String uniqueKey(ChatMessage message, boolean allowSuffix) {
        if (!message.id().isEmpty()) {
            return message.id();
        }
        String base = message.stableKey();
        if (!allowSuffix || !byKey.containsKey(base)) {
            return base;
        }
        String candidate;
        do {
            anonymousSequence++;
            candidate = base + "#" + anonymousSequence;
        } while (byKey.containsKey(candidate));
        return candidate;
    }

    /** Cheap guard used by tests and the UI: no duplicate ids in the transcript. */
    public boolean hasUniqueIds() {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (ChatMessage message : snapshotView) {
            if (message.id().isEmpty()) {
                continue;
            }
            Integer count = seen.get(message.id());
            seen.put(message.id(), count == null ? 1 : count + 1);
        }
        for (Integer count : seen.values()) {
            if (count > 1) {
                return false;
            }
        }
        return true;
    }
}
