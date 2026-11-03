package com.rgb42.footy.wear

import android.app.Application
import com.rgb42.footy.StepService

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Nach jeder Zählung (gedrosselt) die Daten ans Handy übergeben
        StepService.afterRecord = { WatchSync.sync(it) }
    }
}
