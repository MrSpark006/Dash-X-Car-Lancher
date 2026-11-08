package hu.dashx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "Orbit" dial: a 270 degree ring of 61 radial segments.
 *  - speed mode: segments light up with GPS speed (0..240 km/h), accent fades toward white
 *  - clock mode: segments sweep with the seconds, like a clock face
 * No OBD needed, speed comes from GPS.
 */
class DialView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    companion object {
        private const val MAXV = 240f
        private const val STEPS = 60
    }

    var accent: Int = 0xFF22E3FF.toInt()
        set(v) {
            field = v
            invalidate()
        }

    private var clock = false
    private var target = -1f
    private var shown = 0f
    private var time = "--:--"
    private var date = ""
    private var sec = 0

    private val d = resources.displayMetrics.density
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    /** speed < 0 means "no GPS fix". */
    fun update(clockMode: Boolean, speed: Float, timeText: String, dateText: String, seconds: Int) {
        clock = clockMode
        target = speed
        time = timeText
        date = dateText
        sec = seconds
        invalidate()
    }

    private fun fit(p: Paint, text: String, size: Float, maxW: Float) {
        p.textSize = size
        val w = p.measureText(text)
        if (w > maxW) p.textSize = size * maxW / w
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f - 4 * d
        if (r <= 0f) return

        // smooth needle-like easing, only animates while the value is moving
        val goal = if (clock || target < 0f) 0f else target
        if (abs(goal - shown) > 0.4f) {
            shown += (goal - shown) * 0.18f
            postInvalidateOnAnimation()
        } else {
            shown = goal
        }

        glow.shader = RadialGradient(cx, cy, r, withAlpha(accent, 0x30), 0x00000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, glow)

        val litIdx = if (clock) sec else if (shown < 0.5f) -1 else (shown / MAXV * STEPS).toInt()
        val majorEvery = if (clock) 5 else 10

        for (i in 0..STEPS) {
            val a = Math.toRadians(135.0 + 270.0 * i / STEPS)
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val major = i % majorEvery == 0
            val r1 = r * (if (major) 0.76f else 0.82f)
            val r2 = r * 0.97f
            val on = i <= litIdx
            seg.color = when {
                on -> lerpColor(accent, Color.WHITE, 0.65f * i / STEPS)
                major -> 0x55FFFFFF
                else -> 0x24FFFFFF
            }
            seg.strokeWidth = r * (if (i == litIdx) 0.05f else 0.03f)
            c.drawLine(cx + ca * r1, cy + sa * r1, cx + ca * r2, cy + sa * r2, seg)

            if (!clock && major) {
                small.color = 0x77FFFFFF
                small.textSize = r * 0.075f
                val v = (i * (MAXV / STEPS)).roundToInt().toString()
                c.drawText(v, cx + ca * r * 0.64f, cy + sa * r * 0.64f + small.textSize * 0.35f, small)
            }
        }

        if (clock) {
            big.color = Color.WHITE
            fit(big, time, r * 0.40f, r * 1.15f)
            c.drawText(time, cx, cy + big.textSize * 0.30f, big)
            val dt = date.uppercase()
            small.color = accent
            fit(small, dt, r * 0.11f, r * 1.1f)
            c.drawText(dt, cx, cy + r * 0.38f, small)
        } else {
            val txt = if (target < 0f) "--" else shown.roundToInt().toString()
            big.color = Color.WHITE
            fit(big, txt, r * 0.52f, r * 1.0f)
            c.drawText(txt, cx, cy + big.textSize * 0.30f, big)
            small.color = accent
            fit(small, "KM/H", r * 0.12f, r * 0.6f)
            c.drawText("KM/H", cx, cy + r * 0.46f, small)
            small.color = 0xAAFFFFFF.toInt()
            fit(small, time, r * 0.14f, r * 0.7f)
            c.drawText(time, cx, cy - r * 0.40f, small)
        }
    }
}
