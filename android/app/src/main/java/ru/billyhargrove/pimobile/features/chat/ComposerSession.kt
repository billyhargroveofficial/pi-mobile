package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Collections

/** Main-thread draft policy. Editor values and prepared attachments are opaque, never encoded or recycled here. */
internal class ComposerSession<Value, Attachment>(private val readOnly: Boolean, private val editor: Editor<Value>) {
    interface Editor<Value> {
        fun text(value: Value): String
        fun selectionStart(value: Value): Int
        fun create(text: String, caret: Int): Value
    }
    class Submission<Attachment> internal constructor(val text: String, val attachments: List<Attachment>,
        internal val owner: Any, internal val revision: Long)

    private var current by mutableStateOf(editor.create("", 0))
    private var revision = 0L
    private var closed by mutableStateOf(false)
    var value: Value
        get() = current
        set(value) {
            if (!closed && current != value) { current = value; revision++ }
        }
    var attachments by mutableStateOf<List<Attachment>>(emptyList()); private set
    var preparing by mutableStateOf(false); private set
    var transcribing by mutableStateOf(false); private set
    val editable get() = !readOnly && !closed
    val occupied get() = editor.text(current).isNotEmpty() || attachments.isNotEmpty() || preparing
    val voice get() = editor.text(current).isBlank() && attachments.isEmpty()

    fun transcriptionChanged(value: Boolean) { if (!closed) transcribing = value }
    fun beginPreparing(): Boolean {
        if (!editable || preparing) return false
        preparing = true
        return true
    }
    fun prepared(values: List<Attachment>) {
        if (closed) return
        preparing = false
        if (editable && values.isNotEmpty()) replaceAttachments(attachments + values)
    }
    fun removeAttachment(index: Int) {
        if (!editable || preparing || index !in attachments.indices) return
        replaceAttachments(attachments.filterIndexed { i, _ -> i != index })
    }
    fun insertDictation(text: String) {
        if (!editable || text.isBlank()) return
        val previous = editor.text(current)
        val at = editor.selectionStart(current).coerceIn(0, previous.length)
        val inserted = (if (at > 0) " " else "") + text
        value = editor.create(previous.substring(0, at) + inserted + previous.substring(at), at + inserted.length)
    }
    fun selectSkill(name: String) { if (editable) setText("\$$name ") }
    fun clearMatchingText(text: String) {
        if (editable && editor.text(current).trim() == text) setText("")
    }
    fun restore(text: String, values: List<Attachment> = emptyList()): Boolean {
        if (!editable || occupied) return false
        setText(text)
        replaceAttachments(values.toList())
        return true
    }
    fun submission(): Submission<Attachment>? = if (editable && !preparing)
        Submission(editor.text(current), attachments, this, revision) else null

    /** Only the exact submitted draft may be consumed; reentrant edits, even change-and-back, win. */
    fun sent(submission: Submission<Attachment>) {
        if (!editable || submission.owner !== this || revision != submission.revision) return
        setText("")
        replaceAttachments(emptyList())
    }
    fun close() {
        closed = true
        preparing = false
        transcribing = false
    }
    private fun setText(text: String) { value = editor.create(text, text.length) }
    private fun replaceAttachments(values: List<Attachment>) {
        if (attachments == values) return
        attachments = if (values.isEmpty()) emptyList() else Collections.unmodifiableList(values)
        revision++
    }
}
