package dev.galaxy.toolbox.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

internal object WidgetBatteryArc {
    fun draw(percent: Int?, kind: ConnectedBattery.Kind, accent: Int, textColor: Int, showGlyph: Boolean = true): Bitmap {
        val image = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND }
        val ring = RectF(17f, 17f, 103f, 103f)
        paint.color = (textColor and 0x00FFFFFF) or 0x50000000
        canvas.drawArc(ring, 135f, 270f, false, paint)
        if (percent != null) {
            paint.color = if (percent < 20) 0xFFE7A55B.toInt() else accent
            canvas.drawArc(ring, 135f, percent.coerceIn(0, 100) * 2.7f, false, paint)
        }
        if (!showGlyph) return image
        paint.color = textColor
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        when (kind) {
            ConnectedBattery.Kind.PHONE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(48f, 42f, 72f, 80f, 4f, 4f, paint)
                canvas.drawPoint(60f, 74f, paint)
            }
            ConnectedBattery.Kind.HEADPHONES -> {
                canvas.drawArc(RectF(45f, 42f, 75f, 72f), 195f, 150f, false, paint)
                canvas.drawLine(45f, 62f, 45f, 76f, paint)
                canvas.drawLine(75f, 62f, 75f, 76f, paint)
            }
            ConnectedBattery.Kind.WATCH -> {
                canvas.drawRoundRect(47f, 49f, 73f, 73f, 5f, 5f, paint)
                canvas.drawLine(54f, 39f, 54f, 49f, paint)
                canvas.drawLine(66f, 39f, 66f, 49f, paint)
                canvas.drawLine(54f, 73f, 54f, 83f, paint)
                canvas.drawLine(66f, 73f, 66f, 83f, paint)
            }
            ConnectedBattery.Kind.ACCESSORY -> {
                canvas.drawRoundRect(46f, 46f, 74f, 74f, 6f, 6f, paint)
                canvas.drawCircle(60f, 60f, 3f, paint)
            }
        }
        return image
    }
}
