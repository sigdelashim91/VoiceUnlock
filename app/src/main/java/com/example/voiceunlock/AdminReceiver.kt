package com.example.voiceunlock

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/** Only used to turn the screen off (for the test, since the phone has no power button). */
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(c: Context) = ComponentName(c, AdminReceiver::class.java)
        fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
        fun isActive(c: Context) = dpm(c).isAdminActive(component(c))
        fun screenOff(c: Context): Boolean =
            try { if (isActive(c)) { dpm(c).lockNow(); true } else false } catch (_: Throwable) { false }
        fun remove(c: Context) = try { dpm(c).removeActiveAdmin(component(c)) } catch (_: Throwable) {}
    }
}
