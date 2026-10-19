package school.greenwood.plus.ui.components

import kotlin.math.pow

/** Use local dark coverage, not whole-bar/whole-icon average luminance. */
internal fun useLightGlassForeground(pixels: IntArray, wasLight: Boolean): Boolean {
    var valid = 0
    var dark = 0
    fun linear(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    pixels.forEach { argb ->
        if ((argb ushr 24) < 128) return@forEach
        valid++
        val luminance = 0.2126 * linear(argb shr 16 and 255) +
            0.7152 * linear(argb shr 8 and 255) + 0.0722 * linear(argb and 255)
        if (luminance < 0.18) dark++
    }
    if (valid == 0) return wasLight
    // A single dark edge row in the 9×9 probe is sufficient; isolated pixels are not.
    return dark.toFloat() / valid >= if (wasLight) 0.04f else 0.10f
}
