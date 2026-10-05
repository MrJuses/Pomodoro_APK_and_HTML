package dk.decpt.focustaper

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Same palette as the HTML version, light and dark. */
class Palette(val night: Boolean) {
    val bg = if (night) 0xFF0E1519.toInt() else 0xFFEDF1F2.toInt()
    val surface = if (night) 0xFF162127.toInt() else 0xFFFFFFFF.toInt()
    val fg = if (night) 0xFFE4ECEF.toInt() else 0xFF13202A.toInt()
    val muted = if (night) 0xFF8C9EA6.toInt() else 0xFF5A6B74.toInt()
    val line = if (night) 0xFF25343B.toInt() else 0xFFD2DADE.toInt()
    val work = if (night) 0xFFF06B83.toInt() else 0xFFC93A55.toInt()
    val brk = if (night) 0xFF3CC29D.toInt() else 0xFF1E8A6E.toInt()
    val long = if (night) 0xFF8296F6.toInt() else 0xFF3D58D6.toInt()

    fun phase(p: Phase): Int = when (p) {
        Phase.WORK -> work
        Phase.BREAK -> brk
        Phase.LONG -> long
    }

    companion object {
        fun of(ctx: Context): Palette {
            val mode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return Palette(mode == Configuration.UI_MODE_NIGHT_YES)
        }

        /** Linear mix: t = 0 gives a, t = 1 gives b. */
        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
            return Color.rgb(
                ch(Color.red(a), Color.red(b)),
                ch(Color.green(a), Color.green(b)),
                ch(Color.blue(a), Color.blue(b)),
            )
        }

        fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)
    }
}
