package com.rgb42.footy.wear

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.rgb42.footy.Keys
import com.rgb42.footy.StepStore
import java.time.LocalDate

/**
 * Überträgt die Stundenwerte der Uhr ans Handy (Wear-OS-Datenschicht).
 * Stromsparend: höchstens alle 10 Minuten, nicht dringend – das System
 * bündelt die Übertragung mit anderem Bluetooth-Verkehr.
 */
object WatchSync {
    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    private var lastSyncAt = 0L
    private var lastSyncedDay: LocalDate? = null

    fun sync(ctx: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val today = LocalDate.now()
        val dayChanged = lastSyncedDay != null && lastSyncedDay != today
        if (!force && !dayChanged && now - lastSyncAt < MIN_INTERVAL_MS && lastSyncAt != 0L) return
        lastSyncAt = now
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences("footy_sync", Context.MODE_PRIVATE)
        Thread {
            try {
                val store = StepStore.get(app)
                val days = LinkedHashSet<Long>()
                if (!prefs.getBoolean("full_sync_done", false)) {
                    days.addAll(store.ownDays()) // einmalig alles übertragen
                }
                lastSyncedDay?.let { if (it != today) days.add(Keys.dayStart(it)) }
                days.add(Keys.dayStart(today))
                for (d in days) putDay(app, store, d)
                prefs.edit().putBoolean("full_sync_done", true).apply()
                lastSyncedDay = today
            } catch (_: Exception) {
                lastSyncAt = 0L // beim nächsten Mal erneut versuchen
            }
        }.start()
    }

    private fun putDay(ctx: Context, store: StepStore, dayStartKey: Long) {
        val steps = LongArray(24)
        val active = LongArray(24)
        for (b in store.rangeOwn(dayStartKey, dayStartKey + 100)) {
            val h = Keys.hourOf(b.key)
            steps[h] = b.steps
            active[h] = b.activeMs
        }
        val req = PutDataMapRequest.create("/day/${dayStartKey / 100}").apply {
            dataMap.putLongArray("steps", steps)
            dataMap.putLongArray("active", active)
        }
        Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest())
    }
}
