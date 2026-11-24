package com.example.voiceunlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

class VoiceService : Service() {
    private var engine: VoiceEngine? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var lastUnlock = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startAsForeground() } catch (t: Throwable) { stopSelf(); return START_NOT_STICKY }
        if (!ModelManager.isReady(this) || Store.voice(this) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (engine == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // keeps the CPU alive so the mic keeps listening with screen off
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vu:listen").apply { acquire() }
            engine = VoiceEngine(this) { text, spk -> handle(text, spk) }.also { it.start() }
        }
        return START_STICKY
    }

    private fun handle(text: String, spk: DoubleArray?) {
        val want = Store.phrase(this).split(" ").filter { it.isNotBlank() }
        val heard = text.lowercase().split(" ")
        if (want.isEmpty() || !heard.containsAll(want)) return
        val saved = Store.voice(this) ?: return
        if (spk == null) return
        val d = Store.cosineDistance(saved, spk).toFloat()
        Store.setLastDist(this, d)
        val now = System.currentTimeMillis()
        if (d < Store.threshold(this) && now - lastUnlock > 3000) {
            lastUnlock = now
            unlock()
        }
    }

    @Suppress("DEPRECATION")
    private fun unlock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE, "vu:wake"
        ).acquire(5000)
        // Needs "Display over other apps" permission to start from background.
        startActivity(Intent(this, UnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("vu", "Voice Unlock", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "vu")
            .setContentTitle("Voice Unlock is on")
            .setSmallIcon(R.drawable.ic_notify)
            .setContentIntent(open)
            .setContentText("Say your phrase to wake the phone")
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
}
