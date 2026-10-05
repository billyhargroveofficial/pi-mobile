package ru.billyhargrove.pimobile.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import ru.billyhargrove.pimobile.BuildConfig;

/**
 * The single OkHttp client used for REST, WebSocket and media.
 *
 * <p>Security notes:</p>
 * <ul>
 *   <li>no custom {@code TrustManager} or hostname verifier – the platform trust
 *       store decides, so arbitrary/untrusted certificates are never accepted;</li>
 *   <li>in debug builds only, a logging interceptor prints method/host/path/status.
 *       It runs at {@code BASIC} level (never bodies) and the {@code Authorization}
 *       header is explicitly redacted;</li>
 *   <li>the bearer token is never a query parameter: every authenticated call puts
 *       it into a header, and {@code core.EndpointPolicy} cannot even build a URL
 *       that carries it.</li>
 * </ul>
 */
public final class HttpClients {

    private static volatile OkHttpClient shared;

    private HttpClients() {
    }

    public static OkHttpClient get() {
        OkHttpClient local = shared;
        if (local == null) {
            synchronized (HttpClients.class) {
                local = shared;
                if (local == null) {
                    local = build();
                    shared = local;
                }
            }
        }
        return local;
    }

    private static OkHttpClient build() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true);
        if (BuildConfig.DEBUG) {
            okhttp3.logging.HttpLoggingInterceptor logging =
                    new okhttp3.logging.HttpLoggingInterceptor();
            logging.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BASIC);
            logging.redactHeader("Authorization");
            builder.addInterceptor(logging);
        }
        return builder.build();
    }
}
