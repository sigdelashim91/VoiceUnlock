package com.example.voiceunlock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/** Round badge that animates in: circle pops, then a tick (or cross) draws itself. */
class CheckView(ctx: Context, color: Int, private val success: Boolean = true) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var circleT = 1f
    private var markT = 1f
    private val full = Path()
    private val part = Path()
    private val anims = mutableListOf<ValueAnimator>()

    fun play() {
        anims.forEach { it.cancel() }
        anims.clear()
        circleT = 0f; markT = 0f; invalidate()
        anims += ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = OvershootInterpolator(2f)
            addUpdateListener { circleT = it.animatedValue as Float; invalidate() }
            start()
        }
        anims += ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = 220
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener { markT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = min(w, h) / 2f
        c.drawCircle(w / 2f, h / 2f, r * 0.9f * circleT, fill)
        if (markT <= 0f) return
        stroke.strokeWidth = r * 0.16f
        if (success) {
            full.reset()
            full.moveTo(w * 0.30f, h * 0.52f)
            full.lineTo(w * 0.44f, h * 0.65f)
            full.lineTo(w * 0.71f, h * 0.38f)
            val pm = PathMeasure(full, false)
            part.reset()
            pm.getSegment(0f, pm.length * markT, part, true)
            c.drawPath(part, stroke)
        } else {
            val a = min(1f, markT * 2f)
            val b = ((markT - 0.5f) * 2f).coerceIn(0f, 1f)
            val l = w * 0.36f; val t = h * 0.36f; val rr = w * 0.64f; val bb = h * 0.64f
            c.drawLine(l, t, l + (rr - l) * a, t + (bb - t) * a, stroke)
            if (b > 0f) c.drawLine(rr, t, rr - (rr - l) * b, t + (bb - t) * b, stroke)
        }
    }

    override fun onDetachedFromWindow() {
        anims.forEach { it.cancel() }
        super.onDetachedFromWindow()
    }
}
