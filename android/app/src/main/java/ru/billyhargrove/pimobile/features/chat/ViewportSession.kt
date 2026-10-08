package ru.billyhargrove.pimobile.features.chat

import androidx.compose.runtime.*
import ru.billyhargrove.pimobile.core.TranscriptPresentation

/** Reader/follow-tail and arrival policy. Compose alone measures and moves the actual viewport. */
internal class ViewportSession(private val load: () -> Position?, private val save: (Position) -> Unit,
    private val restore: (Position?) -> Unit) {
    data class Position(val key: String, val offset: Int, val follow: Boolean)
    var followTail by mutableStateOf(true); private set
    var tailRevision by mutableIntStateOf(0); private set
    var smoothTail by mutableStateOf(false); private set
    var arrivalRevision by mutableIntStateOf(0); private set
    var arrivingKeys by mutableStateOf<Set<String>>(emptySet()); private set
    var newActivity by mutableStateOf(false); private set
    var catchingUp = false; private set
    private var firstRendered = false
    private var viewportLoaded = false
    private var readerRevision = 0L
    private var closed = false

    fun start() { if (!closed) catchingUp = firstRendered }
    fun save(key: String, offset: Int) { if (!closed && key.isNotEmpty()) save(Position(key, offset, followTail)) }
    fun restoreCached() {
        if (closed || viewportLoaded) return
        viewportLoaded = true
        val revision = readerRevision
        val saved = load() ?: return
        if (closed || readerRevision != revision) return
        firstRendered = true; followTail = false; restore(saved)
    }
    fun viewportRestored() {
        if (closed) return
        restore(null)
        if (followTail) { smoothTail = true; tailRevision++ }
    }
    fun readerDragged() {
        if (closed) return
        readerRevision++; viewportLoaded = true; followTail = false; restore(null)
    }
    fun readerSettled(atBottom: Boolean) { if (!closed) { followTail = atBottom; if (atBottom) newActivity = false } }
    fun arrivalsShown(revision: Int) { if (!closed && revision == arrivalRevision) arrivingKeys = emptySet() }
    fun published(prior: List<TranscriptPresentation.Item>, next: List<TranscriptPresentation.Item>,
        animateUpdates: Boolean, animateAdded: Boolean) {
        if (closed || !firstRendered || (!animateUpdates && !animateAdded)) return
        val previous = prior.associateBy { it.row.key }
        val arrivals = mutableSetOf<String>()
        for (item in next) {
            val old = previous[item.row.key]
            if (old == null || (animateUpdates && (old.row.message != item.row.message || old.tools != item.tools || old.expanded != item.expanded)))
                arrivals.add(item.row.key)
        }
        if (arrivals.isNotEmpty()) { arrivingKeys = arrivals; arrivalRevision++; if (!followTail) newActivity = true }
    }
    fun rendered(hasItems: Boolean, tailTouched: Boolean) {
        if (closed) return
        val first = !firstRendered && hasItems
        if (first || (followTail && tailTouched)) { smoothTail = !first; tailRevision++ }
        if (first) firstRendered = true
    }
    fun snapshotRendered(cached: Boolean) { if (!closed) catchingUp = cached }
    fun messagesRendered() { if (!closed) catchingUp = false }
    fun tail() {
        if (closed) return
        followTail = true; newActivity = false; restore(null); smoothTail = true; tailRevision++
    }
    fun close() { if (!closed) { closed = true; catchingUp = false; arrivingKeys = emptySet(); newActivity = false; restore(null) } }
}
