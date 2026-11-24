package com.example.voiceunlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.concurrent.thread

class MainActivity : Activity() {
    // Palette
    private val PRIMARY = 0xFF2E7D6B.toInt()
    private val PRIMARY_SOFT = 0xFFE3F1EC.toInt()
    private val BG = 0xFFF4F6F5.toInt()
    private val CARD = 0xFFFFFFFF.toInt()
    private val TEXT = 0xFF1C2421.toInt()
    private val MUTED = 0xFF6B7672.toInt()
    private val LINE = 0xFFE1E6E4.toInt()
    private val OFF = 0xFFE9EDEB.toInt()

    private lateinit var heroCard: LinearLayout
    private lateinit var heroIcon: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroButton: Button

    private lateinit var stepModels: Step
    private lateinit var stepPerms: Step
    private lateinit var stepVoice: Step
    private lateinit var phraseBox: EditText
    private lateinit var sensValue: TextView
    private lateinit var lastMatch: TextView

    private var enrolling: VoiceEngine? = null
    private var downloading = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun text(s: String, size: Float, color: Int = TEXT, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(0f, 1.15f)
        }

    private fun pill(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        minHeight = dp(48)
        setPadding(dp(20), 0, dp(20), 0)
        setTextColor(if (filled) CARD else PRIMARY)
        background = if (filled) rounded(PRIMARY, 24) else rounded(CARD, 24, PRIMARY)
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0, bottom: Int = 0, matchWidth: Boolean = true) = LinearLayout.LayoutParams(
        if (matchWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    /** One setup card: number badge, title, description, optional extra views, action button. */
    inner class Step(private val number: Int, title: String, desc: String, action: String, onAction: () -> Unit) {
        val card = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 20)
            setPadding(dp(20), dp(18), dp(20), dp(20))
            elevation = dp(1).toFloat()
        }
        private val badge = text("$number", 14f, PRIMARY, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) }
        }
        val status = text(desc, 14f, MUTED)
        val extra = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val button = pill(action, filled = false, onAction)

        init {
            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge)
                addView(text(title, 17f, TEXT, bold = true))
            }
            card.addView(header)
            card.addView(status, lp(top = 8))
            card.addView(extra, lp())
            card.addView(button, lp(top = 14, matchWidth = false))
        }

        fun setDone(done: Boolean) {
            badge.text = if (done) "✓" else "$number"
            badge.setTextColor(if (done) CARD else PRIMARY)
            badge.background = rounded(if (done) PRIMARY else PRIMARY_SOFT, 15)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(col) })

        // Header
        col.addView(text("Voice Unlock", 28f, TEXT, bold = true))
        col.addView(text("Wake the phone just by saying a phrase.", 15f, MUTED), lp(top = 4, bottom = 20))

        // Status card
        heroIcon = text("🎙", 34f).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        }
        heroTitle = text("", 20f, TEXT, bold = true).apply { gravity = Gravity.CENTER }
        heroSub = text("", 14f, MUTED).apply { gravity = Gravity.CENTER }
        heroButton = pill("", filled = true) { toggleService() }
        heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            addView(heroIcon)
            addView(heroTitle, lp(top = 12))
            addView(heroSub, lp(top = 4))
            addView(heroButton, LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        }
        col.addView(heroCard, lp(bottom = 24))

        col.addView(text("SETUP", 12f, MUTED, bold = true).apply { letterSpacing = 0.1f }, lp(bottom = 10))

        // Step 1
        stepModels = Step(1, "Voice pack", "", "Download") { downloadModels() }
        col.addView(stepModels.card, lp(bottom = 12))

        // Step 2
        stepPerms = Step(2, "Permissions", "", "Allow") { askPermissions() }
        col.addView(stepPerms.card, lp(bottom = 12))

        // Step 3
        stepVoice = Step(3, "Record voice", "", "Start recording") { enroll() }
        phraseBox = EditText(this).apply {
            setText(Store.phrase(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            setTextColor(TEXT)
            background = rounded(BG, 12, LINE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        stepVoice.extra.addView(text("Unlock phrase (English words)", 13f, MUTED), lp(top = 14, bottom = 6))
        stepVoice.extra.addView(phraseBox, lp())
        col.addView(stepVoice.card, lp(bottom = 12))

        // Sensitivity card
        val sens = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 20)
            setPadding(dp(20), dp(18), dp(20), dp(18))
            elevation = dp(1).toFloat()
        }
        val sensHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sensHeader.addView(text("Sensitivity", 17f, TEXT, bold = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sensValue = text("", 14f, PRIMARY, bold = true)
        sensHeader.addView(sensValue)
        sens.addView(sensHeader)
        sens.addView(text("If it doesn't recognise the voice, move towards Relaxed. If others can unlock it, move towards Strict.", 14f, MUTED), lp(top = 6))
        sens.addView(SeekBar(this).apply {
            max = 100
            progress = (Store.threshold(this@MainActivity) * 100).toInt()
            progressTintList = ColorStateList.valueOf(PRIMARY)
            thumbTintList = ColorStateList.valueOf(PRIMARY)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    Store.setThreshold(this@MainActivity, p / 100f); refresh()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, lp(top = 12))
        val ends = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ends.addView(text("Strict", 12f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        ends.addView(text("Relaxed", 12f, MUTED))
        sens.addView(ends, lp(top = 2))
        lastMatch = text("", 13f, MUTED)
        sens.addView(lastMatch, lp(top = 10))
        col.addView(sens, lp(bottom = 12))

        col.addView(text("Works only when the phone has no PIN, pattern or password lock.", 12f, MUTED).apply {
            gravity = Gravity.CENTER
        }, lp(top = 12))
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun permsOk() = micOk() && Settings.canDrawOverlays(this)

    private fun refresh() {
        val modelsOk = ModelManager.isReady(this)
        val voiceOk = Store.voice(this) != null
        val on = Store.enabled(this)
        val ready = modelsOk && permsOk() && voiceOk

        heroCard.background = rounded(if (on) PRIMARY_SOFT else OFF, 24)
        heroIcon.background = rounded(if (on) PRIMARY else CARD, 36)
        heroTitle.text = when {
            on -> "Listening"
            ready -> "Ready to start"
            else -> "Finish setup below"
        }
        heroSub.text = when {
            on -> "Say \"${Store.phrase(this)}\" to wake the phone."
            ready -> "Tap Start to begin listening in the background."
            else -> "Complete the 3 steps, then come back here."
        }
        heroButton.text = if (on) "Stop" else "Start"
        heroButton.isEnabled = on || ready
        heroButton.alpha = if (heroButton.isEnabled) 1f else 0.4f
        heroButton.background = if (on) rounded(CARD, 24, PRIMARY) else rounded(PRIMARY, 24)
        heroButton.setTextColor(if (on) PRIMARY else CARD)

        stepModels.setDone(modelsOk)
        if (!downloading) stepModels.status.text =
            if (modelsOk) "Downloaded. Works offline now."
            else "One-time download, about 50 MB. Use Wi-Fi."
        stepModels.button.visibility = if (modelsOk) View.GONE else View.VISIBLE

        stepPerms.setDone(permsOk())
        stepPerms.status.text = if (permsOk()) "All set."
            else "Microphone, notifications, show over other apps and unrestricted battery."
        stepPerms.button.visibility = if (permsOk()) View.GONE else View.VISIBLE

        stepVoice.setDone(voiceOk)
        if (enrolling == null) stepVoice.status.text =
            if (voiceOk) "Voice saved. Record again to replace it."
            else "The phone's owner says the phrase 5 times, in a normal voice."
        stepVoice.button.text = when {
            enrolling != null -> "Cancel"
            voiceOk -> "Record again"
            else -> "Start recording"
        }

        val t = Store.threshold(this)
        sensValue.text = when {
            t < 0.35f -> "Strict"
            t < 0.6f -> "Balanced"
            else -> "Relaxed"
        }
        val last = Store.lastDist(this)
        lastMatch.text = if (last >= 0)
            "Last attempt: %s (score %.2f, limit %.2f)".format(if (last < t) "accepted" else "rejected", last, t)
        else "Try it once to see how well the voice matches."
    }

    private fun downloadModels() {
        downloading = true
        stepModels.button.isEnabled = false
        stepModels.status.text = "Starting download…"
        thread {
            var error: String? = null
            try {
                ModelManager.download(this) { m -> runOnUiThread { stepModels.status.text = m } }
            } catch (t: Throwable) {
                error = t.message
            }
            runOnUiThread {
                downloading = false
                stepModels.button.isEnabled = true
                refresh()
                if (error != null) stepModels.status.text = "Download failed. Check internet and try again."
            }
        }
    }

    private fun askPermissions() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Throwable) {}
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res); refresh()
    }

    private fun enroll() {
        if (enrolling != null) { cancelEnroll(); return }
        if (!ModelManager.isReady(this)) { stepVoice.status.text = "Download the voice pack first (step 1)."; return }
        if (!micOk()) { stepVoice.status.text = "Allow the microphone first (step 2)."; return }
        val phrase = phraseBox.text.toString().trim().lowercase()
        if (phrase.isEmpty()) { stepVoice.status.text = "Type a phrase first."; return }
        Store.setPhrase(this, phrase)
        if (Store.enabled(this)) {
            Store.setEnabled(this, false)
            stopService(Intent(this, VoiceService::class.java))
        }

        val samples = mutableListOf<DoubleArray>()
        val want = phrase.split(" ").filter { it.isNotBlank() }
        var engine: VoiceEngine? = null
        engine = VoiceEngine(
            this,
            onReady = {
                runOnUiThread {
                    if (enrolling === engine) stepVoice.status.text = "Listening… say \"$phrase\"  (0 of 5)"
                }
            },
            onError = { msg ->
                runOnUiThread {
                    if (enrolling === engine) {
                        enrolling = null
                        refresh()
                        stepVoice.status.text = "Couldn't use the microphone: $msg. Close other apps using it and try again."
                    }
                }
            }
        ) { heard, spk ->
            if (samples.size >= 5) return@VoiceEngine
            if (spk != null && heard.lowercase().split(" ").containsAll(want)) {
                samples.add(spk)
                val n = samples.size
                if (n >= 5) {
                    val avg = DoubleArray(samples[0].size) { i -> samples.sumOf { it[i] } / samples.size }
                    Store.saveVoice(this, avg)
                    runOnUiThread {
                        if (enrolling === engine) {
                            val e = enrolling
                            enrolling = null
                            thread { e?.stop() }
                            refresh()
                            stepVoice.status.text = "Voice saved. Tap Start at the top."
                        }
                    }
                } else {
                    runOnUiThread { stepVoice.status.text = "Got it. Say it again  ($n of 5)" }
                }
            } else {
                runOnUiThread {
                    stepVoice.status.text = "Heard \"$heard\". Please say \"$phrase\"  (${samples.size} of 5)"
                }
            }
        }
        val eng = engine!!
        enrolling = eng
        refresh()
        stepVoice.status.text = "Getting the microphone ready…"
        eng.start()
    }

    private fun cancelEnroll() {
        val e = enrolling
        enrolling = null
        thread { e?.stop() }
        refresh()
    }

    private fun toggleService() {
        if (Store.enabled(this)) {
            Store.setEnabled(this, false)
            stopService(Intent(this, VoiceService::class.java))
        } else {
            if (enrolling != null) cancelEnroll()
            Store.setEnabled(this, true)
            startForegroundService(Intent(this, VoiceService::class.java))
        }
        refresh()
    }

    override fun onDestroy() {
        enrolling?.stop()
        super.onDestroy()
    }
}
