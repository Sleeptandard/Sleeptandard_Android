package com.leejang.sleeptandard.Potch

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/** Closed files are immutable. Export survives service teardown; failed exports remain retryable. */
object PotchLogExporter {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    fun isClosed(context: Context, name: String): Boolean = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences("potch_log_exports", Context.MODE_PRIVATE)
        name in prefs.getStringSet("pending", emptySet()).orEmpty() ||
            name in prefs.getStringSet("completed", emptySet()).orEmpty()
    }

    fun enqueue(context: Context, name: String) {
        val app = context.applicationContext
        synchronized(lock) {
            val prefs = app.getSharedPreferences("potch_log_exports", Context.MODE_PRIVATE)
            if (name in prefs.getStringSet("completed", emptySet()).orEmpty()) return
            val pending = prefs.getStringSet("pending", emptySet()).orEmpty() + name
            check(prefs.edit().putStringSet("pending", pending).commit())
        }
        retryPending(app)
    }

    fun retryPending(context: Context) {
        val app = context.applicationContext
        executor.execute {
            val prefs = app.getSharedPreferences("potch_log_exports", Context.MODE_PRIVATE)
            val names = synchronized(lock) { prefs.getStringSet("pending", emptySet()).orEmpty().toList() }
            names.forEach { name ->
                // File copies must not hold the lock used by BLE/main-thread enqueue calls.
                val success = runCatching {
                    PotchDataLogger.exportInternalLogFilesToDownloads(app, listOf(name)).isNotEmpty()
                }.getOrDefault(false)
                if (success) synchronized(lock) {
                    val pending = prefs.getStringSet("pending", emptySet()).orEmpty() - name
                    val completed = prefs.getStringSet("completed", emptySet()).orEmpty() + name
                    prefs.edit().putStringSet("pending", pending).putStringSet("completed", completed).commit()
                }
                else Log.e("PotchLogExporter", "Export pending; internal file retained: $name")
            }
        }
    }
}
