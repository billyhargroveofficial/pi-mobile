package ru.billyhargrove.pimobile;

import android.app.Application;
import android.content.Context;

import okhttp3.OkHttpClient;
import ru.billyhargrove.pimobile.net.HttpApi;
import ru.billyhargrove.pimobile.net.HttpClients;
import ru.billyhargrove.pimobile.net.MediaLoader;
import ru.billyhargrove.pimobile.net.PiClient;
import ru.billyhargrove.pimobile.store.SettingsStore;

/**
 * Holds the single gateway connection, so rotating the device or moving between
 * the catalog and a chat does not tear down the WebSocket.
 */
public final class PiApp extends Application {

    private static PiApp instance;

    private SettingsStore settings;
    private OkHttpClient http;
    private HttpApi api;
    private PiClient client;
    private MediaLoader mediaLoader;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new SettingsStore(this);
        http = HttpClients.get();
        api = new HttpApi(http);
        client = new PiClient(http, api);
        mediaLoader = new MediaLoader(api, settings);
        if (settings.hasConnection()) {
            client.connect(settings.baseUrl(), settings.token());
        }
    }

    public static PiApp get(Context context) {
        Context app = context.getApplicationContext();
        if (app instanceof PiApp) {
            return (PiApp) app;
        }
        if (instance == null) {
            throw new IllegalStateException("PiApp ещё не создан");
        }
        return instance;
    }

    public SettingsStore settings() {
        return settings;
    }

    public HttpApi api() {
        return api;
    }

    public PiClient client() {
        return client;
    }

    public MediaLoader mediaLoader() {
        return mediaLoader;
    }

    public OkHttpClient http() {
        return http;
    }
}
