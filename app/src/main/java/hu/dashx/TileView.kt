package hu.dashx

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** One configurable launcher button, drawn directly on a Canvas (no child views = very light). */
class TileView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var info: TileInfo? = null
        set(v) {
            field = v
            invalidate()
        }

    var accent: Int = 0xFF22E3FF.toInt()
        set(v) {
            field = v
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.08f
    }
    private val rect = RectF()

    init {
        isClickable = true
        isFocusable = true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4 * d
        rect.set(pad, pad, w - pad, h - pad)
        val r = min(w, h) * 0.16f
        val down = isPressed

        // glass body
        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            if (down) withAlpha(accent, 0x70) else 0x30FFFFFF,
            if (down) withAlpha(accent, 0x30) else 0x0CFFFFFF,
            Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, r, r, fill)

        // border
        line.strokeWidth = 1.2f * d
        line.color = withAlpha(accent, if (down) 0xFF else 0x55)
        c.drawRoundRect(rect, r, r, line)

        // accent "glow bar" on top edge
        line.strokeWidth = 3f * d
        line.color = accent
        c.drawLine(w / 2 - w * 0.08f, rect.top + 1.5f * d, w / 2 + w * 0.08f, rect.top + 1.5f * d, line)

        val inf = info ?: return

        // icon
        val isz = min(w, h * 0.8f) * 0.40f
        val cy = h * 0.42f
        val ic = inf.icon
        if (ic != null) {
            ic.setBounds(
                (w / 2 - isz / 2).toInt(), (cy - isz / 2).toInt(),
                (w / 2 + isz / 2).toInt(), (cy + isz / 2).toInt()
            )
            ic.draw(c)
        } else {
            drawGlyph(c, inf.glyph, w / 2, cy, isz / 2)
        }

        // label
        txt.textSize = min(h * 0.13f, w * 0.12f)
        val label = TextUtils.ellipsize(inf.label, txt, w - 6 * pad, TextUtils.TruncateAt.END)
        c.drawText(label, 0, label.length, w / 2, h * 0.80f, txt)
    }

    private fun drawGlyph(c: Canvas, g: Glyph, cx: Float, cy: Float, r: Float) {
        line.color = accent
        line.strokeWidth = r * 0.14f
        fill.shader = null
        fill.color = accent
        when (g) {
            Glyph.RADIO -> {
                c.drawCircle(cx, cy, r * 0.16f, fill)
                for (k in 1..2) {
                    val rr = r * 0.42f * k
                    val o = RectF(cx - rr, cy - rr, cx + rr, cy + rr)
                    c.drawArc(o, -45f, 90f, false, line)
                    c.drawArc(o, 135f, 90f, false, line)
                }
            }
            Glyph.GRID -> {
                for (i in -1..1) for (j in -1..1) {
                    c.drawCircle(cx + i * r * 0.62f, cy + j * r * 0.62f, r * 0.16f, fill)
                }
            }
            Glyph.PLUS -> {
                c.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, line)
                c.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.5f, line)
            }
        }
    }
}
