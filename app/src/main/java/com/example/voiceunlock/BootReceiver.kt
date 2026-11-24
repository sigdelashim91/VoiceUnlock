package com.example.voiceunlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Store.enabled(c)) return
        // Android 14+ blocks starting a microphone service straight from boot.
        // With "Display over other apps" granted we may open a invisible
        // activity instead; being visible, it is allowed to start the service.
        try {
            if (Settings.canDrawOverlays(c)) {
                c.startActivity(Intent(c, BootActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                c.startForegroundService(Intent(c, VoiceService::class.java))
            }
        } catch (_: Throwable) {}
    }
}
