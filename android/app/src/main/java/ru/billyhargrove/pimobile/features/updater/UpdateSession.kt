package ru.billyhargrove.pimobile.features.updater

import androidx.compose.runtime.*
import java.io.File
import ru.billyhargrove.pimobile.core.ReleaseUpdate

/** Main-thread owner. Request identity fences late progress, duplicate results and closed hosts. */
internal class UpdateSession(private val version: String, private val port: Port,
    private val clock: () -> Long = System::currentTimeMillis, private val show: (Panel?) -> Unit = {}) {
    interface Port {
        fun lastCheck(): Long
        fun dismissed(): String
        fun rememberCheck(time: Long)
        fun rememberDismissed(version: String)
        fun check(done: (Result<ReleaseUpdate?>) -> Unit): () -> Unit
        fun download(update: ReleaseUpdate, progress: (Int) -> Unit, done: (Result<File>) -> Unit): () -> Unit
        fun allowedToInstall(): Boolean
        fun openSettings()
        fun install(apk: File)
    }
    sealed interface Panel {
        data class Offer(val update: ReleaseUpdate) : Panel
        data object Progress : Panel
        data object Permission : Panel
    }
    var status by mutableStateOf(""); private set
    var progress by mutableIntStateOf(0); private set
    var panel: Panel? = null; private set
    private class Request { var cancel: (() -> Unit)? = null }
    private var request: Request? = null
    private var pending: File? = null
    private var awaitingSettings = false
    private var closed = false

    fun check(manual: Boolean) {
        if (closed || request != null || panel != null || pending != null) return
        val now = clock()
        if (!manual && now - port.lastCheck() < 6 * 60 * 60 * 1000) return
        val ticket = Request(); request = ticket
        if (manual) status = "Checking for updates…"
        val done: (Result<ReleaseUpdate?>) -> Unit = done@ { result ->
            if (!owns(ticket)) return@done
            request = null
            result.fold({ update ->
                port.rememberCheck(now)
                if (update == null) { if (manual) status = "You are up to date · $version" }
                else {
                    status = "Update available · ${update.version}"
                    if (manual || update.version != port.dismissed()) changePanel(Panel.Offer(update))
                }
            }, { if (manual) status = "Update check failed: ${it.message ?: "Network error"}" })
        }
        try { attach(ticket, port.check(done)) } catch (cause: Exception) { done(Result.failure(cause)) }
    }
    fun accept() {
        if (closed) return
        when (val current = panel) {
            is Panel.Offer -> download(current.update)
            Panel.Permission -> {
                awaitingSettings = true; changePanel(null)
                try { port.openSettings() } catch (cause: Exception) { installFailed(cause) }
            }
            else -> Unit
        }
    }
    fun dismiss() {
        if (closed) return
        when (val current = panel) {
            is Panel.Offer -> port.rememberDismissed(current.update.version)
            Panel.Progress -> cancelRequest()
            Panel.Permission -> { pending = null; awaitingSettings = false }
            null -> return
        }
        changePanel(null)
    }
    private fun download(update: ReleaseUpdate) {
        val ticket = Request(); request = ticket; progress = 0; changePanel(Panel.Progress)
        val done: (Result<File>) -> Unit = done@ { result ->
            if (!owns(ticket)) return@done
            request = null; changePanel(null)
            result.fold({ apk ->
                pending = apk
                try { if (port.allowedToInstall()) install() else changePanel(Panel.Permission) }
                catch (cause: Exception) { installFailed(cause) }
            }, { status = "Update failed: ${it.message ?: "Download failed"}" })
        }
        try {
            attach(ticket, port.download(update, { value -> if (owns(ticket)) progress = maxOf(progress, value.coerceIn(0, 100)) }, done))
        } catch (cause: Exception) { done(Result.failure(cause)) }
    }
    fun resume() {
        if (closed || !awaitingSettings || pending == null) return
        try {
            if (port.allowedToInstall()) install()
            else { awaitingSettings = false; changePanel(Panel.Permission) }
        } catch (cause: Exception) { installFailed(cause) }
    }
    private fun install() {
        val apk = pending ?: return
        // Consume the explicit action before invoking Android, including on failure/reentrancy.
        pending = null; awaitingSettings = false
        try { port.install(apk) } catch (cause: Exception) { installFailed(cause) }
    }
    private fun installFailed(cause: Exception) {
        pending = null; awaitingSettings = false; changePanel(null)
        status = "Cannot install update: ${cause.message}"
    }
    fun close() {
        if (closed) return
        closed = true; cancelRequest(); pending = null; awaitingSettings = false; changePanel(null)
    }
    private fun changePanel(value: Panel?) { panel = value; show(value) }
    private fun owns(ticket: Request) = !closed && request === ticket
    private fun attach(ticket: Request, cancel: () -> Unit) { if (owns(ticket)) ticket.cancel = cancel else cancel() }
    private fun cancelRequest() {
        val previous = request; request = null
        try { previous?.cancel?.invoke() } catch (_: Exception) { /* State is already retired. */ }
    }
}
