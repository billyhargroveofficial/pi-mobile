package ru.billyhargrove.pimobile.net

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/** Bounded blocking pool; all presentation mutations return to the main looper. */
object AppExecutors {
    private val counter = AtomicInteger(1)
    private val io = Executors.newFixedThreadPool(4) { action -> Thread(action, "pi-io-${counter.getAndIncrement()}").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    @JvmStatic fun io(): ExecutorService = io
    @JvmStatic fun main(action: Runnable) { if (isMainThread()) action.run() else main.post(action) }
    @JvmStatic fun mainDelayed(action: Runnable, delayMs: Long) { main.postDelayed(action, delayMs) }
    @JvmStatic fun cancelMain(action: Runnable) { main.removeCallbacks(action) }
    @JvmStatic fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()
}
