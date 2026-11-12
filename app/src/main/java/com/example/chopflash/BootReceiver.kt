package com.example.chopflash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the listener after a reboot or app update if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Prefs.enabled(Prefs.get(context))) {
                    try {
                        ChopService.start(context)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
