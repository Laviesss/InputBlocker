package com.inputblocker.app

import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Monitors config file changes via FileObserver (inotify) with a
 * polling fallback for filesystems that don't support inotify.
 */
class ConfigFileObserver(
    private val configPath: String,
    private val onConfigChanged: () -> Unit,
    /** Injectable for testing – defaults to main-looper handler */
    internal val handler: Handler = Handler(Looper.getMainLooper())
) {
    companion object {
        private const val TAG = "ConfigFileObserver"
        private const val FALLBACK_POLL_MS = 5000L
    }

    private var fileObserver: FileObserver? = null
    private var fallbackThread: Thread? = null
    @Volatile private var lastModified = 0L
    private val running = AtomicBoolean(false)

    private val reloadRunnable = Runnable {
        if (running.get()) {
            onConfigChanged()
        }
    }

    fun startWatching() = start()

    fun stopWatching() = stop()

    fun start() {
        if (running.getAndSet(true)) return

        val configFile = File(configPath)
        val parentDir = configFile.parentFile

        if (parentDir == null || !parentDir.exists()) {
            Log.w(TAG, "Config directory does not exist, using fallback polling")
            startFallback()
            return
        }

        lastModified = configFile.lastModified()

        try {
            fileObserver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(parentDir, FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == null) return
                        if (path == configFile.name || path.endsWith(".conf")) {
                            val now = configFile.lastModified()
                            if (now > lastModified) {
                                lastModified = now
                                handler.removeCallbacks(reloadRunnable)
                                handler.postDelayed(reloadRunnable, 300L)
                            }
                        }
                    }
                }
            } else {
                object : FileObserver(configFile.absolutePath) {
                    override fun onEvent(event: Int, path: String?) {
                        val now = configFile.lastModified()
                        if (now > lastModified) {
                            lastModified = now
                            handler.removeCallbacks(reloadRunnable)
                            handler.postDelayed(reloadRunnable, 300L)
                        }
                    }
                }
            }
            fileObserver?.startWatching()
            Log.i(TAG, "FileObserver started on ${parentDir.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "FileObserver failed (${e.message}), falling back to polling")
            fileObserver = null
            startFallback()
        }
    }

    fun stop() {
        running.set(false)
        fileObserver?.stopWatching()
        fileObserver = null
        fallbackThread?.interrupt()
        fallbackThread = null
        handler.removeCallbacks(reloadRunnable)
    }

    /** Force an immediate config reload */
    fun notifyChanged() {
        handler.removeCallbacks(reloadRunnable)
        handler.post(reloadRunnable)
    }

    private fun startFallback() {
        val thread = Thread {
            while (running.get()) {
                try {
                    Thread.sleep(FALLBACK_POLL_MS)
                    if (!running.get()) break
                    val configFile = File(configPath)
                    val modified = configFile.lastModified()
                    if (modified > lastModified) {
                        lastModified = modified
                        handler.post(reloadRunnable)
                    }
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback poll error", e)
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        fallbackThread = thread
        Log.i(TAG, "Fallback polling started (every ${FALLBACK_POLL_MS}ms)")
    }
}
