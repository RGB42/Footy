package com.rgb42.footy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Startet die Zählung nach Neustart oder App-Update automatisch. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                if (Prefs(context).tracking) StepService.start(context)
        }
    }
}
