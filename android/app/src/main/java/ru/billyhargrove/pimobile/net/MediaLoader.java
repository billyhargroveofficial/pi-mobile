package ru.billyhargrove.pimobile.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.ArrayList;
import android.util.LruCache;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ru.billyhargrove.pimobile.core.MediaUrlPolicy;
import ru.billyhargrove.pimobile.store.SettingsStore;

/**
 * Loads transcript images with the bearer token, but only from the configured
 * origin.
 *
 * <p>{@link MediaUrlPolicy} decides whether a URL may be fetched at all. If the
 * gateway ever returned an absolute URL on another host, the loader refuses it and
 * reports "внешний источник заблокирован" – the token is never sent elsewhere.</p>
 *
 * <p>Decoding happens on the background pool with a bounded sample size, and a
 * small in-memory LRU cache keeps repeated snapshots cheap. Nothing is written to
 * disk.</p>
 */
public final class MediaLoader {

    public interface Callback {
        void onLoaded(String resolvedUrl, Bitmap bitmap);

        void onFailed(String resolvedUrl, String message);
    }

    private static final int MAX_DECODED_EDGE_PX = 1600;
    private static final int CACHE_BYTES = 12 * 1024 * 1024;

    private final HttpApi api;
    private final SettingsStore settings;
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    private final Map<String, List<Callback>> inFlight = new ConcurrentHashMap<>();

    public MediaLoader(HttpApi api, SettingsStore settings) {
        this.api = api;
        this.settings = settings;
    }

    public void load(final String rawUrl, final Callback callback) {
        final String baseUrl = settings.baseUrl();
        final String token = settings.token();
        final String resolved = MediaUrlPolicy.resolve(baseUrl, rawUrl);
        if (resolved == null) {
            AppExecutors.main(new Runnable() {
                @Override
                public void run() {
                    callback.onFailed("", "Внешний источник заблокирован: изображение не с этого сервера");
                }
            });
            return;
        }

        Bitmap cached;
        synchronized (cache) {
            cached = cache.get(resolved);
        }
        if (cached != null) {
            final Bitmap bitmap = cached;
            AppExecutors.main(new Runnable() {
                @Override
                public void run() {
                    callback.onLoaded(resolved, bitmap);
                }
            });
            return;
        }

        List<Callback> waiters = inFlight.get(resolved);
        if (waiters != null) {
            synchronized (waiters) {
                waiters.add(callback);
            }
            return;
        }
        waiters = new ArrayList<>();
        waiters.add(callback);
        inFlight.put(resolved, waiters);

        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = null;
                String error = null;
                try {
                    byte[] bytes = api.fetchMedia(baseUrl, token, rawUrl);
                    bitmap = decodeBounded(bytes);
                    if (bitmap == null) {
                        error = "Не удалось декодировать изображение";
                    }
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Не удалось загрузить изображение" : e.getMessage();
                }
                final Bitmap result = bitmap;
                final String failure = error;
                List<Callback> callbacks = inFlight.remove(resolved);
                if (result != null) {
                    synchronized (cache) {
                        cache.put(resolved, result);
                    }
                }
                AppExecutors.main(new Runnable() {
                    @Override
                    public void run() {
                        if (callbacks == null) {
                            return;
                        }
                        for (Callback cb : callbacks) {
                            if (result != null) {
                                cb.onLoaded(resolved, result);
                            } else {
                                cb.onFailed(resolved, failure == null ? "Ошибка загрузки" : failure);
                            }
                        }
                    }
                });
            }
        });
    }

    public void clearCache() {
        synchronized (cache) {
            cache.evictAll();
        }
    }

    private static Bitmap decodeBounded(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        int maxEdge = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxEdge / sample > MAX_DECODED_EDGE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
