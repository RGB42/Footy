package com.rgb42.footy.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.rgb42.footy.Prefs

/** Übernimmt Schrittlänge, Gewicht und Tagesziel vom Handy. */
class SettingsListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val prefs = Prefs(this)
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != "/settings") continue
            val m = DataMapItem.fromDataItem(e.dataItem).dataMap
            m.getInt("stride_cm", 0).takeIf { it in 30..150 }?.let { prefs.strideCm = it }
            m.getInt("weight_kg", 0).takeIf { it in 20..300 }?.let { prefs.weightKg = it }
            m.getInt("goal", 0).takeIf { it in 100..100_000 }?.let { prefs.goal = it }
        }
    }
}
