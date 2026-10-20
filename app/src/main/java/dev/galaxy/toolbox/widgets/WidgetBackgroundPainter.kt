package dev.galaxy.toolbox.widgets

import android.graphics.*

/** RemoteViews carries only this static bitmap, never Compose, RenderEffect or wallpaper capture. */
object WidgetBackgroundPainter {
    fun create(settings: WidgetSettings, width: Int, height: Int): Bitmap? {
        if (!settings.drawsBackground) return null
        // Bound the RemoteViews bitmap payload across multiple responsive sizes.
        val scale = minOf(1f, 320f / maxOf(width, height).coerceAtLeast(1))
        val w = (width * scale).toInt().coerceAtLeast(2)
        val h = (height * scale).toInt().coerceAtLeast(2)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = minOf(28f * scale, minOf(w, h) / 4f)
            paint.color = settings.backgroundColor
            val layer = canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), settings.opacity.coerceIn(0, 100) * 255 / 100)
            paint.alpha = if (settings.drawsDecoration) 96 else 255
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (settings.drawsDecoration) {
                fun tint(color: Int, fraction: Float) = (color and 0xFFFFFF) or ((255 * fraction).toInt().coerceIn(0, 255) shl 24)
                val clipped = canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
                paint.alpha = 255
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(tint(Color.WHITE, .20f), tint(settings.accentColor, .025f), tint(Color.BLACK, .10f), tint(Color.WHITE, .16f)),
                    floatArrayOf(0f, .38f, .72f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                paint.shader = RadialGradient(w * .06f, h * -.15f, maxOf(w, h) * .85f,
                    intArrayOf(tint(Color.WHITE, .30f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                paint.shader = RadialGradient(w * 1.03f, h * .95f, maxOf(w, h) * .65f,
                    intArrayOf(tint(settings.accentColor, .17f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                canvas.restoreToCount(clipped)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(tint(Color.WHITE, .82f), tint(Color.WHITE, .12f), tint(settings.accentColor, .18f), tint(Color.WHITE, .65f)),
                    floatArrayOf(0f, .36f, .65f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, radius, radius, paint)
                val inner = RectF(rect).apply { inset(2f * scale, 2f * scale) }
                paint.strokeWidth = maxOf(.6f, .7f * scale)
                paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(tint(Color.WHITE, .24f), Color.TRANSPARENT, tint(Color.WHITE, .13f)), null, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(inner, (radius - 2f * scale).coerceAtLeast(0f), (radius - 2f * scale).coerceAtLeast(0f), paint)
            }
            canvas.restoreToCount(layer)
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
