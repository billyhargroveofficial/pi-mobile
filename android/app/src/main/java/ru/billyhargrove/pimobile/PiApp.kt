package ru.billyhargrove.pimobile

import android.app.Application
import android.content.Context
import okhttp3.OkHttpClient
import ru.billyhargrove.pimobile.net.*
import ru.billyhargrove.pimobile.store.SettingsStore
import java.io.File

/** Process composition keeps one gateway across activity navigation/recreation. */
class PiApp : Application() {
    private lateinit var settings: SettingsStore
    private lateinit var http: OkHttpClient
    private lateinit var api: HttpApi
    private lateinit var client: PiClient
    private lateinit var mediaLoader: MediaLoader
    override fun onCreate() {
        super.onCreate(); instance = this
        settings = SettingsStore(this); http = HttpClients.get(); api = HttpApi(http)
        client = PiClient(http, api).apply { cacheDirectory(File(noBackupFilesDir, "conversations")) }
        mediaLoader = MediaLoader(api, settings)
        if (settings.hasConnection()) client.connect(settings.baseUrl(), settings.token())
    }
    fun settings() = settings
    fun http() = http
    fun api() = api
    fun client() = client
    fun mediaLoader() = mediaLoader
    companion object {
        private var instance: PiApp? = null
        @JvmStatic fun get(context: Context): PiApp = context.applicationContext as? PiApp ?: instance ?: error("PiApp ещё не создан")
    }
}
