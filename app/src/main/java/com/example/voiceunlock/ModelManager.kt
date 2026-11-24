package com.example.voiceunlock

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Downloads the two Vosk models (speech + speaker) once, then works offline. */
object ModelManager {
    private const val SPEECH = "vosk-model-small-en-us-0.15"
    private const val SPK = "vosk-model-spk-0.4"
    private const val BASE = "https://alphacephei.com/vosk/models/"

    private fun root(c: Context) = File(c.filesDir, "models").apply { mkdirs() }
    fun speechDir(c: Context) = File(root(c), SPEECH)
    fun spkDir(c: Context) = File(root(c), SPK)
    fun isReady(c: Context) = speechDir(c).isDirectory && spkDir(c).isDirectory

    /**
     * Blocking. Call from a background thread.
     * onProgress(message, percent 0..100) covers both files as one overall bar.
     */
    fun download(c: Context, onProgress: (String, Int) -> Unit) {
        // name, label, % range for download, % at end of unpacking
        val plan = listOf(
            Triple(SPEECH, "speech model", 0 to 72),
            Triple(SPK, "voice-match model", 75 to 97)
        )
        for ((name, label, range) in plan) {
            val (from, to) = range
            val target = File(root(c), name)
            if (target.isDirectory) continue
            onProgress("Connecting…", from)
            val zip = File(c.cacheDir, "$name.zip")
            val conn = URL("$BASE$name.zip").openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            if (conn.responseCode != 200) throw RuntimeException("server returned ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { inp ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    var lastMb = -1L
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) from + ((to - from) * done / total).toInt() else from
                        val mb = done / 1_048_576
                        if (pct != last || mb != lastMb) {
                            last = pct; lastMb = mb
                            val size = if (total > 0) "$mb of ${total / 1_048_576} MB" else "$mb MB"
                            onProgress("Downloading $label… $size", pct)
                        }
                    }
                }
            }

            onProgress("Unpacking $label…", to)
            val tmp = File(root(c), "$name.tmp")
            tmp.deleteRecursively()
            tmp.mkdirs()
            ZipInputStream(zip.inputStream()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val f = File(tmp, e.name)
                    if (!f.canonicalPath.startsWith(tmp.canonicalPath)) throw SecurityException("bad zip")
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        FileOutputStream(f).use { zin.copyTo(it) }
                    }
                    e = zin.nextEntry
                }
            }
            // zip contains a top-level folder named like the model
            val inner = File(tmp, name)
            if (!(if (inner.isDirectory) inner else tmp).renameTo(target)) throw RuntimeException("could not save model")
            tmp.deleteRecursively()
            zip.delete()
            onProgress("Unpacking $label…", if (name == SPK) 100 else to + 3)
        }
        onProgress("Done", 100)
    }
}
