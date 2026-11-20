package dev.galaxy.toolbox.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import dev.galaxy.toolbox.data.BrandMarks
import kotlin.math.roundToInt

/** Crops the shared transparent vector so its visible height matches the 13sp model label. */
internal object WidgetBrandBitmap {
    fun create(context: Context, mark: String?, textColor: Int): Bitmap? {
        val resource = mark?.let(BrandMarks::drawableResource) ?: return null
        val drawable = ContextCompat.getDrawable(context, resource)?.mutate() ?: return null
        drawable.setTint(textColor)
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, source.width, source.height)
        drawable.draw(Canvas(source))

        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        for (y in 0 until source.height) for (x in 0 until source.width) {
            if (Color.alpha(source.getPixel(x, y)) > 12) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return null
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        val metrics = context.resources.displayMetrics
        val labelHeight = (13f * metrics.scaledDensity).roundToInt().coerceAtLeast(1)
        val maxWidth = (80f * metrics.density).roundToInt().coerceAtLeast(1)
        val width = (cropWidth.toFloat() / cropHeight * labelHeight).roundToInt().coerceIn(1, maxWidth)
        val height = (cropHeight.toFloat() / cropWidth * width).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(Bitmap.createBitmap(source, left, top, cropWidth, cropHeight),
            width, height, true).apply { density = metrics.densityDpi }
    }
}
