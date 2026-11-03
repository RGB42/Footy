package com.rgb42.footy

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Wird vom System gestartet, wenn die Uhr neue Schritte geschickt hat. */
class WatchDataService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        var changed = false
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED) changed = WatchLink.import(this, e.dataItem) || changed
        }
        if (changed) WatchLink.notifyUi()
    }
}
