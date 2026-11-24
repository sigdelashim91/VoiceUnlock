package com.example.voiceunlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Invisible helper: starts the listening service while "in the foreground", then closes. */
class BootActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { startForegroundService(Intent(this, VoiceService::class.java)) } catch (_: Throwable) {}
        finish()
    }
}
