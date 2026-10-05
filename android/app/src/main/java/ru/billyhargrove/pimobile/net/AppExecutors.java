package ru.billyhargrove.pimobile.net;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * All blocking work (HTTP, WebSocket bookkeeping, image decode, file reads) runs
 * on a small background pool; UI touch points are always posted to the main
 * looper. The main thread never performs I/O.
 */
public final class AppExecutors {

    private static final ExecutorService IO = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "pi-io-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }

    public static void main(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN.post(runnable);
        }
    }

    public static void mainDelayed(Runnable runnable, long delayMs) {
        MAIN.postDelayed(runnable, delayMs);
    }

    public static void cancelMain(Runnable runnable) {
        MAIN.removeCallbacks(runnable);
    }

    public static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
