package ru.billyhargrove.pimobile.core

/** One immutable public transcript entry; receipt state never implies agent delivery. */
class ChatMessage @JvmOverloads constructor(id: String?, role: Role?, text: String?, images: List<ImageRef>?,
    toolName: String?, localState: LocalState?, requestId: String?, toolStatus: String? = "done") {
    enum class Role {
        USER, ASSISTANT, TOOL_RESULT, CUSTOM, UNKNOWN;
        companion object {
            @JvmStatic fun parse(raw: String?) = when (raw?.trim()?.lowercase(java.util.Locale.ROOT)) {
                "user" -> USER; "assistant" -> ASSISTANT
                "toolresult", "tool_result", "tool" -> TOOL_RESULT; "custom" -> CUSTOM; else -> UNKNOWN
            }
        }
    }
    enum class LocalState { NONE, SENDING, ACCEPTED, FAILED, UNCERTAIN }
    private val id = id.orEmpty()
    private val role = role ?: Role.UNKNOWN
    private val text = text.orEmpty()
    private val images = java.util.Collections.unmodifiableList(ArrayList(images.orEmpty()))
    private val toolName = toolName.orEmpty()
    private val toolStatus = toolStatus ?: "done"
    private val localState = localState ?: LocalState.NONE
    private val requestId = requestId.orEmpty()
    private var turnId = "legacy"
    private var phase = "answer"
    private var preview = ""
    private var documentPath = ""
    // Private local queue marker; never a wire or agent-control capability.
    private var queueBaseline: List<String>? = null
    fun queued() = queueBaseline != null
    fun queueBaseline(): List<String> = queueBaseline.orEmpty()
    fun withQueue(baseline: List<String>?): ChatMessage = apply { queueBaseline = baseline?.toList() }
    fun id() = id
    fun role() = role
    fun text() = text
    fun images(): List<ImageRef> = images
    fun toolName() = toolName
    fun toolStatus() = toolStatus
    fun localState() = localState
    fun requestId() = requestId
    fun turnId() = turnId
    fun phase() = phase
    fun preview() = preview
    fun documentPath() = documentPath
    val isLocal: Boolean get() = localState != LocalState.NONE
    fun hasImages() = images.isNotEmpty()
    fun stableKey() = id.ifEmpty { "$role:${Integer.toHexString(text.hashCode())}" }
    fun withPresentation(turn: String?, phase: String?, preview: String?, document: String?) =
        ChatMessage(id, role, text, images, toolName, localState, requestId, toolStatus).apply {
            turnId = turn ?: "legacy"; this.phase = phase ?: "answer"
            this.preview = preview.orEmpty(); documentPath = document.orEmpty(); queueBaseline = this@ChatMessage.queueBaseline
        }
    fun withLocalState(state: LocalState?) = ChatMessage(id, role, text, images, toolName, state, requestId, toolStatus)
        .withPresentation(turnId, phase, preview, documentPath).withQueue(queueBaseline)
    override fun equals(other: Any?) = other is ChatMessage && id == other.id && role == other.role &&
        text == other.text && images == other.images && toolName == other.toolName && toolStatus == other.toolStatus &&
        localState == other.localState && requestId == other.requestId && turnId == other.turnId &&
        phase == other.phase && preview == other.preview && documentPath == other.documentPath && queueBaseline == other.queueBaseline
    override fun hashCode() = java.util.Objects.hash(id, role, text, images, toolName, toolStatus, localState,
        requestId, turnId, phase, preview, documentPath, queueBaseline)
    override fun toString() = "ChatMessage{$id, $role, $localState, ${text.length} chars}"
    companion object {
        @JvmStatic fun remote(id: String?, role: Role?, text: String?, images: List<ImageRef>?, toolName: String?) =
            ChatMessage(id, role, text, images, toolName, LocalState.NONE, "")
        @JvmStatic fun local(requestId: String?, text: String?, images: List<ImageRef>?, state: LocalState?) =
            ChatMessage("local:$requestId", Role.USER, text, images, null, state, requestId)
    }
}
