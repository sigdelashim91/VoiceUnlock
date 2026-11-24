package com.example.voiceunlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var phraseBox: EditText
    private lateinit var thrLabel: TextView
    private var enrolling: VoiceEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        setContentView(ScrollView(this).apply { addView(col) })

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; textSize = 18f; setOnClickListener { onClick() }
            col.addView(this)
        }

        col.addView(TextView(this).apply { text = "Voice Unlock"; textSize = 28f })
        status = TextView(this).apply { textSize = 16f; setPadding(0, 20, 0, 20) }
        col.addView(status)

        btn("1. Download voice models (one time, ~50 MB)") { downloadModels() }
        btn("2. Give permissions") { askPermissions() }

        col.addView(TextView(this).apply { text = "Unlock phrase (English):"; setPadding(0, 30, 0, 0) })
        phraseBox = EditText(this).apply {
            setText(Store.phrase(this@MainActivity)); inputType = InputType.TYPE_CLASS_TEXT
        }
        col.addView(phraseBox)

        btn("3. Record dad's voice (say phrase 5 times)") { enroll() }

        thrLabel = TextView(this).apply { setPadding(0, 30, 0, 0) }
        col.addView(thrLabel)
        col.addView(SeekBar(this).apply {
            max = 100
            progress = (Store.threshold(this@MainActivity) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    Store.setThreshold(this@MainActivity, p / 100f); refresh()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        btn("4. START listening service") { startService2() }
        btn("Stop service") {
            Store.setEnabled(this, false)
            stopService(Intent(this, VoiceService::class.java)); refresh()
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val c = this
        val last = Store.lastDist(c)
        status.text = buildString {
            append("Models: ").append(if (ModelManager.isReady(c)) "ready" else "NOT downloaded").append('\n')
            append("Microphone: ").append(if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "ok" else "needed").append('\n')
            append("Display over apps: ").append(if (Settings.canDrawOverlays(c)) "ok" else "needed").append('\n')
            append("Voice recorded: ").append(if (Store.voice(c) != null) "yes" else "no").append('\n')
            append("Service: ").append(if (Store.enabled(c)) "ON" else "off").append('\n')
            if (last >= 0) append("Last match distance: %.2f (must be below limit)".format(last))
        }
        thrLabel.text = "Strictness limit: %.2f  (lower = stricter, raise it if dad's voice is rejected)"
            .format(Store.threshold(c))
    }

    private fun downloadModels() {
        status.text = "Downloading..."
        thread {
            try {
                ModelManager.download(this) { m -> runOnUiThread { status.text = m } }
            } catch (t: Throwable) {
                runOnUiThread { status.text = "Download failed: ${t.message}" }
            }
            runOnUiThread { refresh() }
        }
    }

    private fun askPermissions() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1
        )
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        // stops the phone from killing the service in the background
        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun enroll() {
        if (!ModelManager.isReady(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) { status.text = "Do steps 1 and 2 first."; return }
        val phrase = phraseBox.text.toString().trim().lowercase()
        if (phrase.isEmpty()) { status.text = "Type a phrase first."; return }
        Store.setPhrase(this, phrase)
        stopService(Intent(this, VoiceService::class.java))
        enrolling?.stop()

        val samples = mutableListOf<DoubleArray>()
        val want = phrase.split(" ").filter { it.isNotBlank() }
        status.text = "Dad: say \"$phrase\" clearly, 5 times (pause between). 0/5"
        enrolling = VoiceEngine(this) { text, spk ->
            if (spk != null && text.lowercase().split(" ").containsAll(want)) {
                samples.add(spk)
                runOnUiThread { status.text = "Heard it. ${samples.size}/5" }
                if (samples.size >= 5) {
                    val avg = DoubleArray(samples[0].size) { i -> samples.sumOf { it[i] } / samples.size }
                    Store.saveVoice(this, avg)
                    Thread { enrolling?.stop() }.start()
                    runOnUiThread { refresh(); status.append("\nVoice saved!") }
                }
            } else {
                runOnUiThread { status.text = "Heard \"$text\" - say exactly \"$phrase\". ${samples.size}/5" }
            }
        }.also { it.start() }
    }

    private fun startService2() {
        if (Store.voice(this) == null) { status.text = "Record the voice first (step 3)."; return }
        enrolling?.stop()
        Store.setEnabled(this, true)
        startForegroundService(Intent(this, VoiceService::class.java))
        refresh()
    }
}
