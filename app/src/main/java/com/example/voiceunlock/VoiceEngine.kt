package com.example.voiceunlock

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import kotlin.concurrent.thread

/**
 * Listens to the mic, runs offline speech recognition and returns, for every
 * finished utterance: the recognised text and the speaker "x-vector".
 * Callback runs on the engine thread.
 */
class VoiceEngine(
    private val ctx: Context,
    private val onUtterance: (text: String, spk: DoubleArray?) -> Unit
) {
    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = thread(name = "VoiceEngine") { loop() }
    }

    fun stop() {
        running = false
        worker?.join(1500)
        worker = null
    }

    private fun loop() {
        var model: Model? = null
        var spkModel: SpeakerModel? = null
        var rec: Recognizer? = null
        var audio: AudioRecord? = null
        try {
            model = Model(ModelManager.speechDir(ctx).path)
            spkModel = SpeakerModel(ModelManager.spkDir(ctx).path)
            rec = Recognizer(model, 16000f, spkModel)

            val min = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            @Suppress("MissingPermission")
            audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min, 8192)
            )
            audio.startRecording()
            val buf = ShortArray(2048)
            while (running) {
                val n = audio.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (rec.acceptWaveForm(buf, n)) {
                    val j = JSONObject(rec.result)
                    val text = j.optString("text", "")
                    val arr = j.optJSONArray("spk")
                    val spk = arr?.let { a -> DoubleArray(a.length()) { i -> a.getDouble(i) } }
                    if (text.isNotBlank()) onUtterance(text, spk)
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        } finally {
            try { audio?.stop() } catch (_: Throwable) {}
            try { audio?.release() } catch (_: Throwable) {}
            try { rec?.close() } catch (_: Throwable) {}
            try { spkModel?.close() } catch (_: Throwable) {}
            try { model?.close() } catch (_: Throwable) {}
            running = false
        }
    }
}
