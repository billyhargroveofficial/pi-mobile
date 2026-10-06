package ru.billyhargrove.pimobile.store

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.billyhargrove.pimobile.core.ChatMessage
import ru.billyhargrove.pimobile.core.ImageRef

class PendingMessageCodecTest {
    private fun record(id: String, state: String = "SENDING") = JSONObject().put("id", id)
        .put("text", "Old draft").put("state", state).put("images", JSONArray().put("image/png"))

    @Test fun loadsOriginalJavaRecordsAndTreatsPendingWriteAsUncertain() {
        val loaded = PendingMessageCodec.decode(JSONArray().put(record("legacy")).toString()).single()
        assertEquals("legacy", loaded.requestId())
        assertEquals("Old draft", loaded.text())
        assertEquals(ChatMessage.LocalState.UNCERTAIN, loaded.localState())
        assertEquals(listOf(ImageRef("local:0", "image/png")), loaded.images())
    }

    @Test fun roundTripKeepsSourceIndexAfterOrdinaryFileAttachment() {
        val original = ChatMessage.local("request", "Prompt\n📎 notes.md", listOf(ImageRef("local:2", "image/png")), ChatMessage.LocalState.FAILED)
        val encoded = PendingMessageCodec.encode(listOf(original))
        assertEquals(listOf(original), PendingMessageCodec.decode(encoded))
        // Original Java reader still sees the same MIME array and receipt fields.
        assertEquals("image/png", JSONArray(encoded).getJSONObject(0).getJSONArray("images").getString(0))
        assertFalse(encoded.contains("payload"))
    }

    @Test fun oneMalformedRecordDoesNotDiscardRemainingReceipts() {
        val records = JSONArray().put(record("first", "FAILED")).put(record("bad", "NEW_UNKNOWN_STATE"))
            .put("not an object").put(record("last", "ACCEPTED"))
        assertEquals(listOf("first", "last"), PendingMessageCodec.decode(records.toString()).map { it.requestId() })
    }

    @Test fun malformedCacheAndNonLocalRecordsCannotBecomeOutboxCommands() {
        assertTrue(PendingMessageCodec.decode("broken JSON").isEmpty())
        assertTrue(PendingMessageCodec.decode(JSONArray().put(record("", "FAILED")).put(record("remote", "NONE")).toString()).isEmpty())
        assertEquals("[]", PendingMessageCodec.encode(listOf(ChatMessage.remote("remote", ChatMessage.Role.USER, "text", null, null))))
    }

    @Test fun cachedUrlsStayLocalEvenWithUntrustedExtraFields() {
        val forged = record("request").put("imageUrls", JSONArray().put("https://foreign.invalid/image"))
        assertEquals("local:0", PendingMessageCodec.decode(JSONArray().put(forged).toString()).single().images().single().url())
    }

    @Test fun terminalAndUncertainStatesSurviveRoundTrip() {
        for (state in listOf(ChatMessage.LocalState.ACCEPTED, ChatMessage.LocalState.FAILED, ChatMessage.LocalState.UNCERTAIN)) {
            val message = ChatMessage.local("id", "Text", emptyList(), state)
            assertEquals(listOf(message), PendingMessageCodec.decode(PendingMessageCodec.encode(listOf(message))))
        }
    }
}
