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
            override fun onDismissSucceeded() = done()
            override fun onDismissError() = done()
            override fun onDismissCancelled() = done()
        })
        // safety net
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) done() }, 3000)
    }

    private fun done() {
        if (isFinishing) return
        // a test from the Test screen is waiting for this – tell it it worked
        if (Store.testPending(this)) {
            Store.setTestPending(this, false)
            startActivity(
                android.content.Intent(this, TestActivity::class.java)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    .putExtra("test_ok", true)
            )
        }
        finish()
    }
}
