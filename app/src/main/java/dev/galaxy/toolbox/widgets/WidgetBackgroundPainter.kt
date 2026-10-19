package dev.galaxy.toolbox.widgets

import android.graphics.*

/** RemoteViews carries only this static bitmap, never Compose, RenderEffect or wallpaper capture. */
object WidgetBackgroundPainter {
    fun create(settings: WidgetSettings, width: Int, height: Int): Bitmap? {
        if (!settings.drawsBackground) return null
        // Bound the RemoteViews bitmap payload across multiple responsive sizes.
        val scale = minOf(1f, 192f / maxOf(width, height).coerceAtLeast(1))
        val w = (width * scale).toInt().coerceAtLeast(2)
        val h = (height * scale).toInt().coerceAtLeast(2)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = minOf(28f * scale, minOf(w, h) / 4f)
            paint.color = settings.backgroundColor
            paint.alpha = settings.opacity.coerceIn(0, 100) * 255 / 100
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (settings.drawsDecoration) {
                val alpha = settings.opacity / 100f
                fun tint(color: Int, fraction: Float) = (color and 0xFFFFFF) or ((255 * alpha * fraction).toInt().coerceIn(0, 255) shl 24)
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(tint(Color.WHITE, .16f), tint(settings.accentColor, .05f), tint(Color.WHITE, .02f)), null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.shader = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = tint(Color.WHITE, .30f)
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    fun progress(value: Int, accent: Int): Bitmap = Bitmap.createBitmap(200, 6, Bitmap.Config.ARGB_8888).also {
        val canvas = Canvas(it)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = (accent and 0xFFFFFF) or 0x35000000
        canvas.drawRoundRect(0f, 0f, 200f, 6f, 3f, 3f, paint)
        paint.color = accent
        canvas.drawRoundRect(0f, 0f, 2f * value.coerceIn(0, 100), 6f, 3f, 3f, paint)
    }
}
