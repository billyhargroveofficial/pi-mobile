package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.function.Consumer
import ru.billyhargrove.pimobile.PiApp
import ru.billyhargrove.pimobile.features.catalog.ArchiveSession
import ru.billyhargrove.pimobile.features.catalog.ArchiveScreen
import ru.billyhargrove.pimobile.net.AppExecutors

/** Platform modal and I/O adapter; the catalog owns search, pages and confirmation. */
class ArchiveSheet @JvmOverloads constructor(context: Context, loader: Loader, listener: Listener,
    delete: Delete? = null) : ComposeSheet(context) {
    fun interface Listener { fun resume(id: String, title: String) }
    fun interface Loader { @Throws(Exception::class) fun fetch(offset: Int, query: String): JSONObject }
    fun interface Delete { fun remove(id: String, success: Runnable, failure: Consumer<String>) }
    @JvmOverloads constructor(context: Context, app: PiApp, listener: Listener, delete: Delete? = null) : this(context,
        Loader { offset, query -> app.api().fetchArchive(app.settings().baseUrl(), app.settings().token(), offset, query) }, listener, delete)
    private val handler = Handler(Looper.getMainLooper())
    private var search: Runnable? = null
    private val owner = ArchiveSession(object : ArchiveSession.Transport {
        override fun fetch(offset: Int, query: String, result: (Result<JSONObject>) -> Unit) {
            AppExecutors.io().execute {
                val value = try { Result.success(loader.fetch(offset, query)) } catch (cause: Exception) { Result.failure(cause) }
                AppExecutors.main { result(value) }
            }
        }
        override fun scheduleSearch(delayMs: Long, action: () -> Unit) {
            cancelSearch(); search = Runnable { action() }.also { handler.postDelayed(it, delayMs) }
        }
        override fun cancelSearch() { search?.let(handler::removeCallbacks); search = null }
    }, ZoneId.systemDefault(), LocalDate::now, delete?.let { mutation -> { id, done ->
        mutation.remove(id, Runnable { AppExecutors.main { done(Result.success(Unit)) } },
            Consumer { message -> AppExecutors.main { done(Result.failure(Exception(message))) } })
    } })
    init {
        content { ArchiveScreen(owner, (context.resources.displayMetrics.heightPixels / context.resources.displayMetrics.density * .88f).dp,
            "${context.packageName}:id/", ::dismiss, listener::resume) }
        setOnDismissListener { owner.close() }
        owner.load()
    }
}
