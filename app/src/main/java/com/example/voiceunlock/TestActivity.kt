package com.example.voiceunlock

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Guided test:
 *  1. Voice check – say the phrase with the screen on, see how well it matches.
 *  2. Screen-off test – screen turns off, say the phrase, see if it wakes & unlocks.
 */
class TestActivity : BaseUi() {
    private enum class Screen { LOCKED, READY, COUNTDOWN, WAITING, SUCCESS, FAIL }

    private val ui = Handler(Looper.getMainLooper())

    // voice check
    private lateinit var pulse: PulseView
    private lateinit var voiceStatus: TextView
    private lateinit var matchBox: LinearLayout
    private lateinit var matchPct: TextView
    private lateinit var matchBar: ProgressBar
    private lateinit var voicePass: LinearLayout
    private lateinit var voicePassCheck: CheckView
    private lateinit var voiceRetry: RetryBar
    private lateinit var voiceButton: Button
    private var engine: VoiceEngine? = null
    private var voicePassed = false
    private val voiceTimeout = Runnable { voiceFailed("Didn't hear \"${Store.phrase(this)}\". Speak a little louder and closer.") }

    // screen-off test
    private lateinit var screenCard: LinearLayout
    private lateinit var screenStatus: TextView
    private lateinit var countdown: TextView
    private lateinit var screenButton: Button
    private lateinit var waitLink: TextView
    private var screen = Screen.LOCKED
    private var wentDark = false

    // result
    private lateinit var resultCard: LinearLayout
    private lateinit var resultCheck: CheckView
    private lateinit var resultTitle: TextView
    private lateinit var resultSub: TextView
    private lateinit var resultRetry: RetryBar
    private lateinit var resultDone: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val phrase = Store.phrase(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        smooth(col)
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(col) })

        // top bar
        val back = text("←", 24f, TEXT).apply {
            setPadding(dp(4), dp(4), dp(16), dp(4))
            setOnClickListener { finish() }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(back)
            addView(text("Test it", 24f, TEXT, bold = true))
        })
        col.addView(text("Two quick checks to make sure everything works.", 15f, MUTED), lp(top = 6, bottom = 20))

        // ---- card 1: voice check
        val voiceCard = card()
        voiceCard.addView(text("1 · Voice check", 17f, TEXT, bold = true))
        voiceCard.addView(text("Say the phrase with the screen on. We'll check it's the saved voice.", 14f, MUTED), lp(top = 6))
        pulse = PulseView(this, PRIMARY, PRIMARY_SOFT)
        voiceCard.addView(pulse, LinearLayout.LayoutParams(dp(110), dp(110)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16)
        })
        voiceStatus = text("Tap the button, then say \"$phrase\".", 15f, TEXT).apply { gravity = Gravity.CENTER }
        voiceCard.addView(voiceStatus, lp(top = 8))

        matchPct = text("", 14f, PRIMARY, bold = true)
        matchBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressDrawable = progressDrawable(); isIndeterminate = false
        }
        matchBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(LinearLayout(this@TestActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text("Voice match", 14f, MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(matchPct)
            })
            addView(matchBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(6) })
        }
        voiceCard.addView(matchBox, lp(top = 16))

        voicePassCheck = CheckView(this, PRIMARY)
        voicePass = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(PRIMARY_SOFT, 14)
            setPadding(dp(12), dp(12), dp(16), dp(12))
            visibility = View.GONE
            addView(voicePassCheck, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(12) })
            addView(text("Voice recognised", 15f, TEXT, bold = true))
        }
        voiceCard.addView(voicePass, lp(top = 14))
        voiceRetry = RetryBar { startVoiceCheck() }
        voiceCard.addView(voiceRetry.view, lp(top = 14))

        voiceButton = pill("Start voice check", filled = true) {
            if (engine != null) stopVoiceCheck(resetText = true) else startVoiceCheck()
        }
        voiceCard.addView(voiceButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        col.addView(voiceCard, lp(bottom = 12))

        // ---- card 2: screen-off test
        screenCard = card()
        screenCard.addView(text("2 · Screen-off test", 17f, TEXT, bold = true))
        screenStatus = text("", 14f, MUTED)
        screenCard.addView(screenStatus, lp(top = 6))
        countdown = text("3", 64f, PRIMARY, bold = true).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
        }
        screenCard.addView(countdown, lp(top = 8))
        screenButton = pill("", filled = true) { onScreenButton() }
        screenCard.addView(screenButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        waitLink = text("Or let the screen turn off by itself", 14f, PRIMARY, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
            setOnClickListener { startWaiting(useAdmin = false) }
        }
        screenCard.addView(waitLink, lp())
        col.addView(screenCard, lp(bottom = 12))

        // ---- result
        resultCheck = CheckView(this, PRIMARY)
        resultTitle = text("It works!", 22f, TEXT, bold = true).apply { gravity = Gravity.CENTER }
        resultSub = text("", 14f, MUTED).apply { gravity = Gravity.CENTER }
        resultRetry = RetryBar { screen = Screen.READY; render() }
        resultDone = pill("Done", filled = true) { finish() }
        resultCard = card().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            addView(resultCheck, LinearLayout.LayoutParams(dp(72), dp(72)))
            addView(resultTitle, lp(top = 12))
            addView(resultSub, lp(top = 4))
            addView(resultRetry.view, lp(top = 4))
            addView(resultDone, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        }
        col.addView(resultCard, lp(bottom = 12))

        staggerIn(listOf(voiceCard, screenCard))
        render()
        handleResult(intent)
    }

    // ---------------- voice check ----------------

    private fun startVoiceCheck() {
        voiceRetry.hide()
        voicePass.conceal()
        matchBox.conceal()
        // free the microphone from the background service while we test
        stopService(Intent(this, VoiceService::class.java))
        val phrase = Store.phrase(this)
        val want = phrase.split(" ").filter { it.isNotBlank() }
        voiceStatus.setSmooth("Getting the microphone ready…")
        voiceButton.text = "Cancel"
        var e: VoiceEngine? = null
        e = VoiceEngine(
            this,
            grammar = Store.grammar(this),
            onReady = { runOnUiThread { if (engine === e) { pulse.setActive(true); voiceStatus.setSmooth("Listening… say \"$phrase\"") } } },
            onError = { msg -> runOnUiThread { if (engine === e) voiceFailed("Couldn't use the microphone ($msg).") } }
        ) { heard, spk ->
            runOnUiThread {
                if (engine !== e) return@runOnUiThread
                if (!heard.lowercase().split(" ").containsAll(want)) {
                    voiceStatus.setSmooth("Didn't catch that. Say \"$phrase\" again.")
                    return@runOnUiThread
                }
                if (spk == null) { voiceStatus.setSmooth("A bit too short. Say it once more, normally."); return@runOnUiThread }
                val saved = Store.voice(this) ?: return@runOnUiThread
                val d = Store.cosineDistance(saved, spk).toFloat()
                Store.setLastDist(this, d)
                showMatch(d, d < Store.threshold(this))
            }
        }
        val eng = e!!
        engine = eng
        eng.start()
        ui.removeCallbacks(voiceTimeout)
        ui.postDelayed(voiceTimeout, 15000)
    }

    private fun stopVoiceCheck(resetText: Boolean) {
        ui.removeCallbacks(voiceTimeout)
        val e = engine
        engine = null
        thread { e?.stop() }
        pulse.setActive(false)
        voiceButton.text = if (voicePassed) "Check again" else "Start voice check"
        if (resetText) voiceStatus.setSmooth("Tap the button, then say \"${Store.phrase(this)}\".")
    }

    private fun showMatch(d: Float, pass: Boolean) {
        stopVoiceCheck(resetText = false)
        val pct = ((1f - d) * 100f).coerceIn(0f, 100f).toInt()
        matchBar.progressDrawable = progressDrawable(if (pass) PRIMARY else ERROR, if (pass) PRIMARY_SOFT else ERROR_SOFT)
        matchBar.progress = 0
        matchPct.setTextColor(if (pass) PRIMARY else ERROR)
        matchPct.text = "$pct%"
        matchBox.reveal()
        matchBox.post { matchBar.setProgress(pct * 10, true) }
        if (pass) {
            voicePassed = true
            voiceStatus.setSmooth("That's the saved voice.")
            voicePass.reveal(); voicePassCheck.play(); buzz(voicePass)
            voiceButton.text = "Check again"
            if (screen == Screen.LOCKED) { screen = Screen.READY; render(); bounce(screenCard) }
        } else {
            voiceFailed("The voice didn't match well enough. Say it the way you did when recording, or set Sensitivity a bit more Relaxed.")
        }
    }

    private fun voiceFailed(msg: String) {
        stopVoiceCheck(resetText = false)
        voiceStatus.setSmooth("Not quite.")
        voiceRetry.show(msg)
    }

    // ---------------- screen-off test ----------------

    private fun onScreenButton() {
        when (screen) {
            Screen.READY -> if (AdminReceiver.isActive(this)) startWaiting(useAdmin = true) else askAdmin()
            Screen.COUNTDOWN, Screen.WAITING -> { screen = Screen.READY; Store.setTestPending(this, false); render() }
            else -> {}
        }
    }

    private fun askAdmin() {
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this@TestActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Lets Voice Unlock turn the screen off, so you can test it on a phone without a power button."
            )
        })
    }

    private fun startWaiting(useAdmin: Boolean) {
        if (screen != Screen.READY) return
        if (engine != null) stopVoiceCheck(resetText = true)
        Store.setEnabled(this, true)
        startForegroundService(Intent(this, VoiceService::class.java))
        Store.setTestPending(this, true)
        wentDark = false
        if (useAdmin) {
            screen = Screen.COUNTDOWN
            render()
            runCountdown(3)
        } else {
            screen = Screen.WAITING
            render()
        }
    }

    private fun runCountdown(n: Int) {
        if (screen != Screen.COUNTDOWN) return
        if (n == 0) {
            screen = Screen.WAITING
            render()
            if (!AdminReceiver.screenOff(this)) {
                screenStatus.setSmooth("Couldn't turn the screen off. Let it turn off by itself instead.")
            }
            return
        }
        countdown.text = "$n"
        countdown.scaleX = 0.4f; countdown.scaleY = 0.4f; countdown.alpha = 0f
        countdown.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350)
            .setInterpolator(OvershootInterpolator(2.5f)).start()
        buzz(countdown)
        ui.postDelayed({ runCountdown(n - 1) }, 1000)
    }

    override fun onStop() {
        super.onStop()
        if (screen == Screen.WAITING || screen == Screen.COUNTDOWN) wentDark = true
        if (engine != null) stopVoiceCheck(resetText = true)
    }

    override fun onResume() {
        super.onResume()
        // back from the admin permission screen
        if (screen == Screen.READY) render()
        // phone was woken without our unlock → the test failed
        if (screen == Screen.WAITING && wentDark) {
            Store.setTestPending(this, false)
            val ev = Store.lastEvent(this)
            screen = Screen.FAIL
            render()
            resultRetry.show(
                "The phone didn't wake from your voice." +
                    (if (ev.isNotEmpty()) "\nLast: $ev" else "\nIt didn't hear anything. Speak closer to the phone.")
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleResult(intent)
    }

    private fun handleResult(i: Intent?) {
        if (i?.getBooleanExtra("test_ok", false) != true) return
        i.removeExtra("test_ok")
        voicePassed = true
        screen = Screen.SUCCESS
        render()
        resultCheck.play()
        buzz(resultCard, strong = true)
    }

    // ---------------- drawing ----------------

    private fun render() {
        val phrase = Store.phrase(this)
        val enabled = screen != Screen.LOCKED
        screenCard.animate().alpha(if (enabled) 1f else 0.55f).setDuration(250).start()
        screenButton.isEnabled = enabled
        waitLink.isEnabled = enabled

        when (screen) {
            Screen.LOCKED -> {
                screenStatus.setSmooth("Pass the voice check first.")
                screenButton.text = "Turn screen off & test"
                countdown.conceal(); waitLink.conceal()
            }
            Screen.READY -> {
                val admin = AdminReceiver.isActive(this)
                screenStatus.setSmooth(
                    if (admin) "The screen will turn off in 3 seconds. Wait a moment, then say \"$phrase\"."
                    else "Allow Voice Unlock to turn the screen off, so you can test without a power button."
                )
                screenButton.text = if (admin) "Turn screen off & test" else "Allow turning screen off"
                countdown.conceal(); waitLink.reveal()
            }
            Screen.COUNTDOWN -> {
                screenStatus.setSmooth("Get ready…")
                screenButton.text = "Cancel"
                countdown.reveal(); waitLink.conceal()
            }
            Screen.WAITING -> {
                screenStatus.setSmooth(
                    "Wait for the screen to go dark, count to 2, then say \"$phrase\".\nThe phone should light up and unlock."
                )
                screenButton.text = "Cancel"
                countdown.conceal(); waitLink.conceal()
            }
            Screen.SUCCESS, Screen.FAIL -> {
                screenStatus.setSmooth("Done.")
                screenButton.text = "Run again"
                countdown.conceal(); waitLink.conceal()
            }
        }
        if (screen == Screen.SUCCESS || screen == Screen.FAIL) {
            screenButton.setOnClickListener { screen = Screen.READY; render(); resultCard.conceal() }
        } else {
            screenButton.setOnClickListener { onScreenButton() }
        }

        if (screen == Screen.SUCCESS) {
            resultCheck.visibility = View.VISIBLE
            resultTitle.text = "It works!"
            resultSub.text = "The screen woke up and unlocked from your voice."
            resultTitle.visibility = View.VISIBLE
            resultSub.visibility = View.VISIBLE
            resultRetry.hide()
            resultCard.reveal()
        } else if (screen == Screen.FAIL) {
            resultCheck.visibility = View.GONE
            resultTitle.visibility = View.GONE
            resultSub.visibility = View.GONE
            resultCard.reveal()
        } else {
            resultCard.conceal()
        }
    }

    private fun bounce(v: View) {
        v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(140).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction(null).start()
        }.start()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        engine?.stop()
        if (screen == Screen.COUNTDOWN || screen == Screen.WAITING) Store.setTestPending(this, false)
        // put the background listener back if it was switched on
        if (Store.enabled(this)) try { startForegroundService(Intent(this, VoiceService::class.java)) } catch (_: Throwable) {}
        super.onDestroy()
    }
}
