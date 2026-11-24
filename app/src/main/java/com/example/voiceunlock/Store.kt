package com.example.voiceunlock

import android.content.Context
import kotlin.math.sqrt

/** Small settings + voice-print storage. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("vu", Context.MODE_PRIVATE)

    fun phrase(c: Context): String = p(c).getString("phrase", "open phone") ?: "open phone"
    fun setPhrase(c: Context, v: String) = p(c).edit().putString("phrase", v.trim().lowercase()).apply()

    /** Max cosine distance accepted as "same speaker". Lower = stricter. */
    fun threshold(c: Context): Float = p(c).getFloat("thr", 0.5f)
    fun setThreshold(c: Context, v: Float) = p(c).edit().putFloat("thr", v).apply()

    fun enabled(c: Context): Boolean = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    fun lastDist(c: Context): Float = p(c).getFloat("lastDist", -1f)
    fun setLastDist(c: Context, v: Float) = p(c).edit().putFloat("lastDist", v).apply()

    fun voice(c: Context): DoubleArray? {
        val s = p(c).getString("voice", null) ?: return null
        return s.split(",").map { it.toDouble() }.toDoubleArray()
    }

    fun saveVoice(c: Context, v: DoubleArray) =
        p(c).edit().putString("voice", v.joinToString(",")).apply()

    fun cosineDistance(a: DoubleArray, b: DoubleArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return 1.0 - dot / (sqrt(na) * sqrt(nb))
    }
}
