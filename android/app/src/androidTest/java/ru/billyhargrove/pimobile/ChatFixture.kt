package ru.billyhargrove.pimobile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.json.JSONObject
import ru.billyhargrove.pimobile.core.*
import ru.billyhargrove.pimobile.features.chat.ChatSession

/** Test-only injection into the actual Compose screen. No live command can escape this transport. */
object ChatFixture {
    class Transport : ChatSession.Transport {
        var writes = 0
        var failWrite = false
        var prompting: (() -> Unit)? = null
        val sentPrompts = mutableListOf<Pair<String, List<ImagePayload>>>()
        var reads = 0
        var prompts = 0
        var aborts = 0
        var configurationId: String? = "auto"
        var configurationFailure: Exception? = null
        val configurations = mutableListOf<List<String?>>()
        val commands = mutableListOf<Pair<String, JSONObject>>()
        var liveRequests: Set<String>? = null
        override fun pendingRequests(session: String) = liveRequests
        var savedViewport: JSONObject? = null
        var viewportReads = 0
        override fun connection() = ConnectionState.CONNECTED
        override fun configuration(session: String): JSONObject? = null
        override fun viewport(session: String): JSONObject? { viewportReads++; return savedViewport }
        override fun saveViewport(session: String, key: String, offset: Int, follow: Boolean) {
            savedViewport = JSONObject().put("anchor", key).put("offset", offset).put("follow", follow)
        }
        override fun subscribe(session: String) {}
        override fun prompt(session: String, text: String, payloads: List<ImagePayload>, behavior: CommandBuilder.Behavior): String? {
            prompts++; writes++; sentPrompts.add(text to payloads.toList()); prompting?.invoke()
            return if (failWrite) null else "request-$writes"
        }
        override fun abort(session: String): String { aborts++; writes++; return "abort-$writes" }
        override fun configure(session: String, provider: String?, model: String?, effort: String?, tier: String?): String? {
            writes++; configurations.add(listOf(provider, model, effort, tier)); configurationFailure?.let { throw it }
            return if (configurationId == "auto") "config-$writes" else configurationId
        }
        override fun read(session: String, kind: String, args: JSONObject): String { commands.add(kind to JSONObject(args.toString())); reads++; return "read-$reads" }
    }
    @JvmStatic @JvmOverloads fun install(activity: ChatActivity, session: String, readOnly: Boolean, initial: List<ChatMessage>,
        persist: (List<ChatMessage>) -> Unit = {}): ChatSession {
        // Cancel the activity's asynchronous real-cache restore before replacing its owner.
        // Otherwise a late restore can overwrite a synthetic timeline halfway through a UI test.
        PiApp.get(activity).client().clearListener(activity)
        PiApp.get(activity).client().disconnect()
        val discovery = ChatActivity::class.java.getDeclaredField("orchestration").apply { isAccessible = true }.get(activity) as ru.billyhargrove.pimobile.ui.OrchestrationEntry
        discovery.stop(); discovery.render(JSONObject())
        val effect = ChatActivity::class.java.getDeclaredMethod("effect", ChatSession.Effect::class.java).apply { isAccessible = true }
        val chat = ChatSession(session, "Проверка дизайна", readOnly, Transport(), initial, persist, { effect.invoke(activity, it) })
        chat.onSnapshot(Snapshot(session, SessionStatus.IDLE, true, emptyList(), false))
        val field = ChatActivity::class.java.getDeclaredField("chatState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val state = field.get(activity) as MutableState<ChatSession?>
        state.value = chat
        return chat
    }
    @JvmStatic fun prepareLoading(activity: ChatActivity) {
        install(activity, activity.intent.getStringExtra("session_id").orEmpty(), activity.intent.getBooleanExtra("read_only", false), emptyList())
        val chat = ChatSession(activity.intent.getStringExtra("session_id").orEmpty(), activity.intent.getStringExtra("session_title").orEmpty(),
            activity.intent.getBooleanExtra("read_only", false), Transport(), emptyList(), {}, {})
        val field = ChatActivity::class.java.getDeclaredField("chatState").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val state = field.get(activity) as MutableState<ChatSession?>
        state.value = chat
    }
    @JvmStatic fun orchestration(activity: ChatActivity, data: JSONObject) {
        val field = ChatActivity::class.java.getDeclaredField("orchestration").apply { isAccessible = true }
        (field.get(activity) as ru.billyhargrove.pimobile.ui.OrchestrationEntry).render(data)
    }
    @JvmStatic fun state(activity: ChatActivity): ChatSession {
        val field = ChatActivity::class.java.getDeclaredField("chatState").apply { isAccessible = true }
        return (field.get(activity) as MutableState<*>).value as ChatSession
    }
    @JvmStatic fun list(activity: ChatActivity): LazyListState {
        val field = ChatActivity::class.java.getDeclaredField("transcriptList").apply { isAccessible = true }
        return field.get(activity) as LazyListState
    }
    @JvmStatic fun text(chat: ChatSession, text: String) { chat.composer = TextFieldValue(text, TextRange(text.length)) }
    @JvmStatic fun show(activity: ChatActivity, source: List<ChatMessage>) {
        val chat = state(activity)
        chat.onTimelineMeta(JSONObject().put("sessionId", chat.sessionId).put("activeTurnId", "turn"))
        chat.onSnapshot(Snapshot(chat.sessionId, SessionStatus.RUNNING, true, source, false))
    }
}
