package hu.dashx

fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)

fun lerpColor(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int): Int {
        val x = (a shr s) and 0xFF
        val y = (b shr s) and 0xFF
        return (x + (y - x) * t).toInt()
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
