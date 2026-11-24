package com.example.voiceunlock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/** Round mic badge. When active, soft rings ripple outwards like it's listening. */
class PulseView(ctx: Context, private val color: Int, private val idleBg: Int) : View(ctx) {
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mic = Paint(Paint.ANTI_ALIAS_FLAG)
    private val micStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private var phase = 0f
    private var active = false
    private var anim: ValueAnimator? = null
    private val rect = RectF()

    fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        if (on) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            anim?.cancel(); anim = null; phase = 0f
        }
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f
        val r = outer * 0.62f

        if (active) {
            for (k in 0 until 2) {
                val t = (phase + k * 0.5f) % 1f
                ring.color = color
                ring.alpha = ((1f - t) * 70).toInt()
                c.drawCircle(cx, cy, r + (outer - r) * t, ring)
            }
        }
        circle.color = if (active) color else idleBg
        c.drawCircle(cx, cy, r, circle)

        // microphone glyph
        val fg = if (active) 0xFFFFFFFF.toInt() else color
        mic.color = fg
        micStroke.color = fg
        micStroke.strokeWidth = r * 0.09f
        val w = r * 0.22f
        rect.set(cx - w, cy - r * 0.48f, cx + w, cy + r * 0.10f)
        c.drawRoundRect(rect, w, w, mic)
        rect.set(cx - r * 0.38f, cy - r * 0.30f, cx + r * 0.38f, cy + r * 0.30f)
        c.drawArc(rect, 0f, 180f, false, micStroke)
        c.drawLine(cx, cy + r * 0.30f, cx, cy + r * 0.46f, micStroke)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
