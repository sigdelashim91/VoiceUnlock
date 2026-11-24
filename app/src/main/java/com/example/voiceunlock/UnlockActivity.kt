package com.example.voiceunlock

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/** Invisible activity that shows over the lock screen, turns the screen on and dismisses a swipe-only keyguard. */
@Suppress("DEPRECATION")
class UnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(android.app.NotificationManager::class.java).cancel(VoiceService.WAKE_ID)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
            override fun onDismissError() = finish()
            override fun onDismissCancelled() = finish()
        })
        // safety net
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 3000)
    }
}
