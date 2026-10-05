package dk.decpt.focustaper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** The plan as blocks with widths proportional to their minutes. Tap a block to jump to it. */
@SuppressLint("ViewConstructor")
class TimelineView(
    ctx: Context,
    private val pal: Palette,
    private val mini: Boolean,
    private val onJump: ((Int) -> Unit)? = null,
) : View(ctx) {
    private var segs: List<Seg> = emptyList()
    private var idx = -1
    private var prog = 0f
    private var done = false
    private var starts = FloatArray(0)
    private var widths = FloatArray(0)

    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11 * d
        color = pal.fg
        typeface = android.graphics.Typeface.MONOSPACE
    }
    private val r = RectF()

    fun set(segs: List<Seg>, idx: Int, prog: Float, done: Boolean) {
        this.segs = segs; this.idx = idx; this.prog = prog; this.done = done
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (segs.isEmpty() || width == 0) return
        val gap = if (mini) 1.5f * d else 3f * d
        val rad = if (mini) 1f * d else 4f * d
        val total = segs.sumOf { it.min }.toFloat()
        val avail = width - gap * (segs.size - 1)
        val h = height.toFloat()
        starts = FloatArray(segs.size)
        widths = FloatArray(segs.size)
        var x = 0f
        segs.forEachIndexed { i, s ->
            val w = avail * s.min / total
            starts[i] = x; widths[i] = w
            val col = pal.phase(s.phase)
            r.set(x, 0f, x + w, h)
            if (mini) {
                fill.color = col
                c.drawRoundRect(r, rad, rad, fill)
            } else {
                fill.color = Palette.mix(pal.surface, col, 0.22f)
                c.drawRoundRect(r, rad, rad, fill)
                val past = i < idx || (done && i == idx)
                val now = i == idx && !done
                if (past || now) {
                    fill.color = if (past) Palette.withAlpha(col, 102) else col
                    val fw = if (past) w else w * prog
                    if (fw > 0f) c.drawRoundRect(RectF(x, 0f, x + fw, h), rad, rad, fill)
                }
                if (now) {
                    stroke.color = col
                    val inset = stroke.strokeWidth / 2
                    c.drawRoundRect(RectF(x + inset, inset, x + w - inset, h - inset), rad, rad, stroke)
                }
                if (w > 24 * d && s.min >= 15) {
                    c.drawText(s.min.toString(), x + w / 2, h / 2 + text.textSize / 3, text)
                }
            }
            x += w + gap
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val cb = onJump ?: return super.onTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                for (i in starts.indices) {
                    if (ev.x >= starts[i] && ev.x <= starts[i] + widths[i] + 3 * d) {
                        performClick(); cb(i); return true
                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
