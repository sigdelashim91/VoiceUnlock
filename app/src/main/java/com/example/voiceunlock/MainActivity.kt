package com.example.voiceunlock

import android.Manifest
import android.animation.LayoutTransition
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    private val LINE = 0xFFDCE2DF.toInt()
    private val OFF = 0xFFE9EDEB.toInt()
    private val ERROR = 0xFFC2412D.toInt()
    private val ERROR_SOFT = 0xFFFCECE8.toInt()

    private lateinit var heroCard: LinearLayout
    private lateinit var heroIcon: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var heroButton: Button

    private lateinit var stepModels: Step
    private lateinit var stepPerms: Step
    private lateinit var stepVoice: Step

    // download UI
    private lateinit var dlBox: LinearLayout
    private lateinit var dlBar: ProgressBar
    private lateinit var dlPct: TextView
    private lateinit var dlRetry: RetryBar

    // voice UI
    private lateinit var phraseBox: EditText
    private lateinit var dotsRow: LinearLayout
    private lateinit var dots: List<View>
    private lateinit var savedRow: LinearLayout
    private lateinit var savedCheck: CheckView
    private lateinit var voiceRetry: RetryBar

    private lateinit var sensValue: TextView
    private lateinit var lastMatch: TextView

    private var enrolling: VoiceEngine? = null
    private var downloading = false
    private var downloadError: String? = null
    private var voiceError: String? = null
    private var playSavedAnim = false

    // ---------- small helpers ----------

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

    private fun pill(label: String, filled: Boolean, color: Int = PRIMARY, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        minHeight = dp(44); minimumHeight = dp(44)
        minWidth = 0; minimumWidth = 0
        setPadding(dp(20), 0, dp(20), 0)
        setTextColor(if (filled) CARD else color)
        background = if (filled) rounded(color, 24) else rounded(CARD, 24, color)
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0, bottom: Int = 0, matchWidth: Boolean = true) = LinearLayout.LayoutParams(
        if (matchWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    /** Let a container smoothly resize when children appear/disappear. */
    private fun smooth(v: ViewGroup) {
        v.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            disableTransitionType(LayoutTransition.APPEARING)
            disableTransitionType(LayoutTransition.DISAPPEARING)
            setDuration(240)
        }
    }

    private fun View.reveal(): Boolean {
        if (visibility == View.VISIBLE && alpha == 1f) return false
        animate().cancel()
        if (visibility != View.VISIBLE) { alpha = 0f; translationY = dp(10).toFloat() }
        visibility = View.VISIBLE
        animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).withEndAction(null).start()
        return true
    }

    private fun View.conceal() {
        if (visibility != View.VISIBLE) return
        animate().cancel()
        animate().alpha(0f).translationY(-dp(6).toFloat()).setDuration(180)
            .withEndAction { visibility = View.GONE; translationY = 0f }.start()
    }

    // ---------- reusable pieces ----------

    /** One setup card: number badge, title, description, extra views, action button. */
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
            background = rounded(PRIMARY_SOFT, 15)
        }
        val status = text(desc, 14f, MUTED)
        val extra = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        val button = pill(action, filled = false, onClick = onAction)
        private var done: Boolean? = null

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
            smooth(card); smooth(extra)
        }

        fun setDone(isDone: Boolean) {
            if (done == isDone) return
            val animate = done != null
            done = isDone
            badge.text = if (isDone) "✓" else "$number"
            badge.setTextColor(if (isDone) CARD else PRIMARY)
            badge.background = rounded(if (isDone) PRIMARY else PRIMARY_SOFT, 15)
            if (animate && isDone) {
                badge.scaleX = 0.4f; badge.scaleY = 0.4f
                badge.animate().scaleX(1f).scaleY(1f).setDuration(360)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }
        }
    }

    /** Light red bar: animated ✕ icon, message, "Try again" button. */
    inner class RetryBar(onRetry: () -> Unit) {
        private val icon = CheckView(this@MainActivity, ERROR, success = false)
        val msg = text("", 14f, TEXT)
        val view = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(ERROR_SOFT, 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            visibility = View.GONE
            addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
            addView(msg, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill("Try again", filled = true, color = ERROR, onClick = onRetry).apply {
                textSize = 14f; setPadding(dp(14), 0, dp(14), 0)
                minHeight = dp(38); minimumHeight = dp(38)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply { leftMargin = dp(10) })
        }

        fun show(message: String) {
            msg.text = message
            if (view.reveal()) icon.play()
        }

        fun hide() = view.conceal()
    }

    private fun progressDrawable(): Drawable =
        LayerDrawable(
            arrayOf(
                rounded(PRIMARY_SOFT, 6),
                ClipDrawable(rounded(PRIMARY, 6), Gravity.START, ClipDrawable.HORIZONTAL)
            )
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

    // ---------- screen ----------

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(32))
        }
        smooth(col)
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
            addView(heroButton, LinearLayout.LayoutParams(dp(200), dp(48)).apply { topMargin = dp(16) })
        }
        col.addView(heroCard, lp(bottom = 24))

        col.addView(text("SETUP", 12f, MUTED, bold = true).apply { letterSpacing = 0.1f }, lp(bottom = 10))

        // Step 1: voice pack with progress bar
        stepModels = Step(1, "Voice pack", "", "Download") { downloadModels() }
        dlBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressDrawable = progressDrawable()
            isIndeterminate = false
        }
        dlPct = text("0%", 13f, PRIMARY, bold = true)
        dlBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(dlBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)))
            addView(dlPct, lp(top = 6))
        }
        stepModels.extra.addView(dlBox, lp(top = 14))
        dlRetry = RetryBar { downloadModels() }
        stepModels.extra.addView(dlRetry.view, lp(top = 14))
        col.addView(stepModels.card, lp(bottom = 12))

        // Step 2: permissions
        stepPerms = Step(2, "Permissions", "", "Allow") { askPermissions() }
        col.addView(stepPerms.card, lp(bottom = 12))

        // Step 3: record voice
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

        dots = (0 until 5).map {
            View(this).apply {
                background = rounded(LINE, 7)
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(10) }
            }
        }
        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            dots.forEach { addView(it) }
        }
        stepVoice.extra.addView(dotsRow, lp(top = 16))

        savedCheck = CheckView(this, PRIMARY)
        savedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(PRIMARY_SOFT, 14)
            setPadding(dp(12), dp(12), dp(16), dp(12))
            visibility = View.GONE
            addView(savedCheck, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(text("Voice recorded", 15f, TEXT, bold = true))
                addView(text("Only this voice can unlock the phone.", 13f, MUTED))
            })
        }
        stepVoice.extra.addView(savedRow, lp(top = 16))

        voiceRetry = RetryBar { enroll() }
        stepVoice.extra.addView(voiceRetry.view, lp(top = 16))
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
        val recording = enrolling != null

        // status card
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
        heroButton.animate().alpha(if (heroButton.isEnabled) 1f else 0.4f).setDuration(200).start()
        heroButton.background = if (on) rounded(CARD, 24, PRIMARY) else rounded(PRIMARY, 24)
        heroButton.setTextColor(if (on) PRIMARY else CARD)

        // step 1
        stepModels.setDone(modelsOk)
        if (!downloading) stepModels.status.text =
            if (modelsOk) "Downloaded. Works offline now."
            else "One-time download, about 50 MB. Use Wi-Fi."
        if (downloading) dlBox.reveal() else dlBox.conceal()
        if (downloadError != null && !downloading) dlRetry.show(downloadError!!) else dlRetry.hide()
        if (!modelsOk && !downloading && downloadError == null) stepModels.button.reveal() else stepModels.button.conceal()

        // step 2
        stepPerms.setDone(permsOk())
        stepPerms.status.text = if (permsOk()) "All set."
            else "Microphone, notifications, show over other apps and unrestricted battery."
        if (permsOk()) stepPerms.button.conceal() else stepPerms.button.reveal()

        // step 3
        stepVoice.setDone(voiceOk)
        if (!recording && voiceError == null) stepVoice.status.text =
            "The phone's owner says the phrase 5 times, in a normal voice."
        stepVoice.button.text = when {
            recording -> "Cancel"
            voiceOk -> "Record again"
            else -> "Start recording"
        }
        if (voiceError != null && !recording) stepVoice.button.conceal() else stepVoice.button.reveal()
        if (recording) dotsRow.reveal() else dotsRow.conceal()
        if (voiceOk && !recording && voiceError == null) {
            savedRow.reveal()
            if (playSavedAnim) { playSavedAnim = false; savedCheck.play() }
        } else savedRow.conceal()
        if (voiceError != null && !recording) voiceRetry.show(voiceError!!) else voiceRetry.hide()

        // sensitivity
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

    // ---------- actions ----------

    private fun downloadModels() {
        if (downloading) return
        downloading = true
        downloadError = null
        dlBar.progress = 0
        dlPct.text = "0%"
        stepModels.status.text = "Connecting…"
        refresh()
        thread {
            var error: String? = null
            try {
                ModelManager.download(this) { m, pct ->
                    runOnUiThread {
                        stepModels.status.text = m
                        dlBar.setProgress(pct * 10, true)
                        dlPct.text = "$pct%"
                    }
                }
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
            }
            runOnUiThread {
                downloading = false
                downloadError = if (error != null) "Download failed. Check your internet connection." else null
                refresh()
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

    private fun setDots(n: Int, animateLast: Boolean) {
        dots.forEachIndexed { i, d -> d.background = rounded(if (i < n) PRIMARY else LINE, 7) }
        if (animateLast && n in 1..dots.size) {
            val d = dots[n - 1]
            d.scaleX = 0.2f; d.scaleY = 0.2f
            d.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }
    }

    private fun enroll() {
        if (enrolling != null) { cancelEnroll(); return }
        voiceError = null
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
        setDots(0, false)
        var engine: VoiceEngine? = null
        engine = VoiceEngine(
            this,
            onReady = {
                runOnUiThread {
                    if (enrolling === engine) stepVoice.status.text = "Listening… say \"$phrase\""
                }
            },
            onError = { msg ->
                runOnUiThread {
                    if (enrolling === engine) {
                        enrolling = null
                        voiceError = "Couldn't use the microphone ($msg). Close other apps using it."
                        refresh()
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
                            setDots(5, true)
                            val e = enrolling
                            enrolling = null
                            thread { e?.stop() }
                            playSavedAnim = true
                            // let the last dot pop before switching to the tick
                            dotsRow.postDelayed({ refresh() }, 350)
                        }
                    }
                } else {
                    runOnUiThread {
                        setDots(n, true)
                        stepVoice.status.text = "Got it. Say it again."
                    }
                }
            } else {
                runOnUiThread {
                    stepVoice.status.text = "Heard \"$heard\". Please say \"$phrase\"."
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
