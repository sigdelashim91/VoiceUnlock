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

    /** Blocking. Call from a background thread. */
    fun download(c: Context, onProgress: (String) -> Unit) {
        for (name in listOf(SPEECH, SPK)) {
            val target = File(root(c), name)
            if (target.isDirectory) continue
            onProgress("Downloading $name ...")
            val zip = File(c.cacheDir, "$name.zip")
            val conn = URL("$BASE$name.zip").openConnection() as HttpURLConnection
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.inputStream.use { inp -> FileOutputStream(zip).use { out -> inp.copyTo(out) } }

            onProgress("Unpacking $name ...")
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
            (if (inner.isDirectory) inner else tmp).renameTo(target)
            tmp.deleteRecursively()
            zip.delete()
        }
        onProgress("Models ready.")
    }
}
