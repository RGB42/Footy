package com.rgb42.footy

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Datenaustausch Handy ↔ Uhr über die Wear-OS-Datenschicht.
 * Uhr → Handy: pro Tag ein Eintrag "/day/yyyyMMdd" mit 24 Stundenwerten.
 * Handy → Uhr: "/settings" mit Schrittlänge, Gewicht, Tagesziel.
 */
object WatchLink {
    private val main = Handler(Looper.getMainLooper())

    /** Speichert einen Tages-Eintrag der Uhr. */
    fun import(ctx: Context, item: DataItem): Boolean {
        val path = item.uri.path ?: return false
        if (!path.startsWith("/day/")) return false
        val day = path.removePrefix("/day/").toLongOrNull() ?: return false
        val map = DataMapItem.fromDataItem(item).dataMap
        val steps = map.getLongArray("steps") ?: return false
        val active = map.getLongArray("active") ?: LongArray(24)
        StepStore.get(ctx).replaceWatchDay(day * 100, steps, active)
        return true
    }

    /** Holt beim Öffnen der App alle bereits übertragenen Uhr-Daten nach. */
    fun catchUp(ctx: Context, done: () -> Unit) {
        val app = ctx.applicationContext
        try {
            Wearable.getDataClient(app)
                .getDataItems(Uri.parse("wear://*/day"), DataClient.FILTER_PREFIX)
                .addOnSuccessListener { buf ->
                    Thread {
                        var changed = false
                        try {
                            for (item in buf) changed = import(app, item) || changed
                        } finally {
                            buf.release()
                        }
                        if (changed) main.post(done)
                    }.start()
                }
        } catch (_: Exception) {
            // keine Google-Play-Dienste → ohne Uhr weiter
        }
    }

    fun sendSettings(ctx: Context) {
        val p = Prefs(ctx)
        try {
            val req = PutDataMapRequest.create("/settings").apply {
                dataMap.putInt("stride_cm", p.strideCm)
                dataMap.putInt("weight_kg", p.weightKg)
                dataMap.putInt("goal", p.goal)
            }
            Wearable.getDataClient(ctx.applicationContext).putDataItem(req.asPutDataRequest())
        } catch (_: Exception) {
        }
    }

    fun notifyUi() {
        main.post { StepService.listener?.invoke() }
    }
}
