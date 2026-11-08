package hu.dashx

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Now-playing strip. Left: title + subtitle (tap = open the player).
 * Right: prev / play-pause / next. onAction gets 0=prev 1=play/pause 2=next 3=text area.
 */
class MediaBar @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var accent: Int = 0xFF22E3FF.toInt()
        set(v) {
            field = v
            invalidate()
        }

    var onAction: ((Int) -> Unit)? = null

    private var title = ""
    private var sub = ""
    private var playing = false

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun set(t: String, s: String, isPlaying: Boolean) {
        if (t == title && s == sub && isPlaying == playing) return
        title = t
        sub = s
        playing = isPlaying
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(2 * d, 2 * d, w - 2 * d, h - 2 * d)
        val rad = h * 0.3f

        p.style = Paint.Style.FILL
        p.color = 0x14FFFFFF
        c.drawRoundRect(rect, rad, rad, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = withAlpha(accent, 0x55)
        c.drawRoundRect(rect, rad, rad, p)

        val bx = w * 0.60f
        val tx = h * 0.45f
        val avail = bx - tx - 4 * d

        tp.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        tp.textSize = h * 0.30f
        tp.color = 0xFFFFFFFF.toInt()
        val t1 = TextUtils.ellipsize(title, tp, avail, TextUtils.TruncateAt.END)
        c.drawText(t1, 0, t1.length, tx, h * 0.47f, tp)

        tp.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        tp.textSize = h * 0.22f
        tp.color = accent
        val t2 = TextUtils.ellipsize(sub, tp, avail, TextUtils.TruncateAt.END)
        c.drawText(t2, 0, t2.length, tx, h * 0.77f, tp)

        // buttons
        val bw = (w - bx) / 3f
        val cy = h / 2f
        val s = h * 0.17f
        p.style = Paint.Style.FILL

        // prev
        var cx = bx + bw * 0.5f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(cx - s, cy - s, cx - s + s * 0.3f, cy + s, p)
        path.reset()
        path.moveTo(cx + s, cy - s)
        path.lineTo(cx + s, cy + s)
        path.lineTo(cx - s * 0.6f, cy)
        path.close()
        c.drawPath(path, p)

        // play / pause
        cx = bx + bw * 1.5f
        p.color = accent
        c.drawCircle(cx, cy, s * 1.7f, p)
        p.color = 0xFF0B0D12.toInt()
        if (playing) {
            c.drawRect(cx - s * 0.7f, cy - s, cx - s * 0.2f, cy + s, p)
            c.drawRect(cx + s * 0.2f, cy - s, cx + s * 0.7f, cy + s, p)
        } else {
            path.reset()
            path.moveTo(cx - s * 0.6f, cy - s)
            path.lineTo(cx - s * 0.6f, cy + s)
            path.lineTo(cx + s * 0.9f, cy)
            path.close()
            c.drawPath(path, p)
        }

        // next
        cx = bx + bw * 2.5f
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(cx + s - s * 0.3f, cy - s, cx + s, cy + s, p)
        path.reset()
        path.moveTo(cx - s, cy - s)
        path.lineTo(cx - s, cy + s)
        path.lineTo(cx + s * 0.6f, cy)
        path.close()
        c.drawPath(path, p)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val bx = width * 0.60f
                val idx = if (e.x < bx) 3
                else ((e.x - bx) / ((width - bx) / 3f)).toInt().coerceIn(0, 2)
                onAction?.invoke(idx)
                return true
            }
        }
        return true
    }
}
