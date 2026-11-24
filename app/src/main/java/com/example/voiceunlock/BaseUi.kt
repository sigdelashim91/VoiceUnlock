package com.example.voiceunlock

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look & feel: palette, builders and small animations. */
abstract class BaseUi : Activity() {
    protected val PRIMARY = 0xFF2E7D6B.toInt()
    protected val PRIMARY_SOFT = 0xFFE3F1EC.toInt()
    protected val BG = 0xFFF4F6F5.toInt()
    protected val CARD = 0xFFFFFFFF.toInt()
    protected val TEXT = 0xFF1C2421.toInt()
    protected val MUTED = 0xFF6B7672.toInt()
    protected val LINE = 0xFFDCE2DF.toInt()
    protected val OFF = 0xFFE9EDEB.toInt()
    protected val ERROR = 0xFFC2412D.toInt()
    protected val ERROR_SOFT = 0xFFFCECE8.toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    protected fun rounded(color: Int, radius: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    protected fun text(s: String, size: Float, color: Int = TEXT, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(0f, 1.15f)
        }

    /** Rounded button that gently shrinks while pressed. */
    @SuppressLint("ClickableViewAccessibility")
    protected fun pill(label: String, filled: Boolean, color: Int = PRIMARY, onClick: () -> Unit) = Button(this).apply {
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
        setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    protected fun lp(top: Int = 0, bottom: Int = 0, matchWidth: Boolean = true) = LinearLayout.LayoutParams(
        if (matchWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    protected fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 20)
        setPadding(dp(20), dp(18), dp(20), dp(20))
        elevation = dp(1).toFloat()
        smooth(this)
    }

    /** Let a container smoothly resize when children appear/disappear. */
    protected fun smooth(v: ViewGroup) {
        v.layoutTransition = LayoutTransition().apply {
            enableTransitionType(LayoutTransition.CHANGING)
            disableTransitionType(LayoutTransition.APPEARING)
            disableTransitionType(LayoutTransition.DISAPPEARING)
            setDuration(240)
        }
    }

    protected fun View.reveal(): Boolean {
        if (visibility == View.VISIBLE && alpha == 1f) return false
        animate().cancel()
        if (visibility != View.VISIBLE) { alpha = 0f; translationY = dp(10).toFloat() }
        visibility = View.VISIBLE
        animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).withEndAction(null).start()
        return true
    }

    protected fun View.conceal() {
        if (visibility != View.VISIBLE) return
        animate().cancel()
        animate().alpha(0f).translationY(-dp(6).toFloat()).setDuration(180)
            .withEndAction { visibility = View.GONE; translationY = 0f }.start()
    }

    /** Cross-fade a label to new text (no-op if unchanged). */
    protected fun TextView.setSmooth(s: CharSequence) {
        if (text.toString() == s.toString()) return
        animate().cancel()
        animate().alpha(0f).setDuration(110).withEndAction {
            text = s
            animate().alpha(1f).setDuration(160).withEndAction(null).start()
        }.start()
    }

    /** Cards slide up one after another when the screen opens. */
    protected fun staggerIn(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(24).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L * i).setDuration(380)
                .setInterpolator(DecelerateInterpolator(2f))
                .withEndAction { v.animate().startDelay = 0 }.start()
        }
    }

    protected fun buzz(v: View, strong: Boolean = false) {
        v.performHapticFeedback(
            if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    protected fun progressDrawable(fill: Int = PRIMARY, track: Int = PRIMARY_SOFT): Drawable =
        LayerDrawable(
            arrayOf(
                rounded(track, 6),
                ClipDrawable(rounded(fill, 6), Gravity.START, ClipDrawable.HORIZONTAL)
            )
        ).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }

    /** Light red bar: animated ✕ icon, message, "Try again" button. */
    inner class RetryBar(onRetry: () -> Unit) {
        private val icon = CheckView(this@BaseUi, ERROR, success = false)
        val msg = text("", 14f, TEXT)
        val view = LinearLayout(this@BaseUi).apply {
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
            if (view.reveal()) { icon.play(); buzz(view, strong = true) }
        }

        fun hide() = view.conceal()
    }
}
