package com.example.voiceunlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class VoiceService : Service() {
    private var engine: VoiceEngine? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var lastUnlock = 0L
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startAsForeground() } catch (t: Throwable) {
            Store.setLastEvent(this, "Couldn't start in background: ${t.message}")
            stopSelf(); return START_NOT_STICKY
        }
        if (!ModelManager.isReady(this) || Store.voice(this) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (engine == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // keeps the CPU alive so the mic keeps listening with screen off
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vu:listen").apply { acquire() }
            engine = VoiceEngine(
                this,
                grammar = Store.grammar(this),
                onReady = { Store.setLastEvent(this, "Listening") },
                onError = { msg -> Store.setLastEvent(this, "Microphone error: $msg") }
            ) { text, spk -> handle(text, spk) }.also { it.start() }
        }
        return START_STICKY
    }

    private fun handle(text: String, spk: DoubleArray?) {
        val want = Store.phrase(this).split(" ").filter { it.isNotBlank() }
        val heard = text.lowercase().split(" ")
        if (want.isEmpty() || !heard.containsAll(want)) {
            if (text.isNotBlank()) Store.setLastEvent(this, "Heard \"$text\" (not the phrase)")
            return
        }
        val saved = Store.voice(this) ?: return
        if (spk == null) {
            Store.setLastEvent(this, "Heard the phrase, but too short to check the voice")
            return
        }
        val d = Store.cosineDistance(saved, spk).toFloat()
        val thr = Store.threshold(this)
        Store.setLastDist(this, d)
        if (d >= thr) {
            Store.setLastEvent(this, "Phrase heard, voice didn't match (%.2f, limit %.2f)".format(d, thr))
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastUnlock > 3000) {
            lastUnlock = now
            Store.setLastEvent(this, "Unlocked (voice score %.2f)".format(d))
            main.post { unlock() }
        }
    }

    @Suppress("DEPRECATION")
    private fun unlock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE, "vu:wake"
        ).acquire(5000)

        val open = Intent(this, UnlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        // Way 1: full-screen notification. Android's official way to open a screen
        // over the lock screen from the background.
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val pi = PendingIntent.getActivity(
                this, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, "vu_wake")
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Unlocking…")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build()
            nm.notify(WAKE_ID, n)
            main.postDelayed({ nm.cancel(WAKE_ID) }, 4000)
        } catch (_: Throwable) {}

        // Way 2: open directly (works when "Display over other apps" is allowed).
        try { startActivity(open) } catch (_: Throwable) {}
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("vu", "Voice Unlock", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel("vu_wake", "Wake screen", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "vu")
            .setContentTitle("Voice Unlock is on")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentText("Say your phrase to wake the phone")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        cpuLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object { const val WAKE_ID = 2 }
}
