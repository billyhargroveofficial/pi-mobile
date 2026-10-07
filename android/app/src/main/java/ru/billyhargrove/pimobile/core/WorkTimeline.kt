package ru.billyhargrove.pimobile.core

/** Late events retain their turn; standalone progress splits chronological tool groups. */
object WorkTimeline {
    class Row(@JvmField val key: String, @JvmField val turn: String, @JvmField val group: String,
        @JvmField val message: ChatMessage?, @JvmField val header: Boolean, @JvmField val last: Boolean,
        @JvmField val progress: Boolean, @JvmField val count: Int) {
        fun work() = header || progress || key.startsWith("work-item:")
        companion object { @JvmStatic fun body(turn: String, first: ChatMessage) = Row("work-item:body:$turn", turn, turn, first, false, true, false, 0) }
    }
    @JvmStatic fun build(messages: List<ChatMessage>, collapsed: Set<String>): List<Row> {
        val out = mutableListOf<Row>()
        for ((turn, entries) in messages.groupBy(ChatMessage::turnId)) {
            val tools = mutableListOf<ChatMessage>()
            val answer = entries.lastOrNull { it.role() == ChatMessage.Role.ASSISTANT }?.takeUnless { it.phase() == "work" }
            for (message in entries) {
                if (message === answer) continue
                if (message.role() in setOf(ChatMessage.Role.USER, ChatMessage.Role.ASSISTANT)) {
                    appendTools(out, tools, turn, collapsed); tools.clear()
                    val progress = message.role() == ChatMessage.Role.ASSISTANT
                    // A later answer changes presentation, never the reader's item identity.
                    val key = message.stableKey()
                    out.add(Row(key, turn, key, message, false, false, progress, 0))
                } else tools.add(message)
            }
            appendTools(out, tools, turn, collapsed)
            if (answer != null) out.add(Row(answer.stableKey(), turn, answer.stableKey(), answer, false, false, false, 0))
        }
        return out
    }
    private fun appendTools(out: MutableList<Row>, tools: List<ChatMessage>, turn: String, collapsed: Set<String>) {
        if (tools.isEmpty()) return
        val group = "tools:$turn:${tools.first().stableKey()}"
        val closed = group in collapsed
        out.add(Row("work-header:$group", turn, group, null, true, closed, false, tools.size))
        if (!closed) tools.forEachIndexed { i, message -> out.add(Row("work-item:${message.stableKey()}", turn, group, message, false, i == tools.lastIndex, false, 0)) }
    }
}
