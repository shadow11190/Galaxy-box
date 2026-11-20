package dev.galaxy.toolbox.ui

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.min

/** Draws inside the Compose background capture, including animated GIF frames. */
@Composable
internal fun CustomBackgroundLayer(preset: BackgroundPreset, media: BackgroundMedia?, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { androidx.compose.runtime.mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var frame by remember(media?.movie) { mutableIntStateOf(0) }
    LaunchedEffect(media?.movie, resumed) {
        if (media?.movie != null && resumed) while (isActive) {
            frame++
            delay(50) // Animated backgrounds update only while the Activity is resumed.
        }
    }
    val dark = preset.luminance * preset.brightness < .55f
    Canvas(modifier.fillMaxSize().blur(preset.blur.dp)) {
        val source = media
        if (source != null) drawIntoCanvas { canvas ->
            @Suppress("UNUSED_VARIABLE") val currentFrame = frame
            val native = canvas.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val factor = preset.brightness.coerceIn(.25f, 1.5f)
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                factor, 0f, 0f, 0f, 0f,
                0f, factor, 0f, 0f, 0f,
                0f, 0f, factor, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )))
            drawBackgroundImage(native, source, preset, size.width, size.height, paint)
        }
        drawRect(if (dark) Color.Black else Color.White, alpha = preset.overlay.coerceIn(0f, .8f))
    }
}

internal fun sourceCrop(media: BackgroundMedia, preset: BackgroundPreset, screenRatio: Float): RectF {
    val targetRatio = when (preset.aspect) {
        BackgroundAspect.FREE -> media.width.toFloat() / media.height.coerceAtLeast(1)
        BackgroundAspect.SCREEN -> screenRatio
        else -> preset.aspect.ratio ?: screenRatio
    }.coerceAtLeast(.1f)
    var width = media.width.toFloat()
    var height = media.height.toFloat()
    if (width / height > targetRatio) width = height * targetRatio else height = width / targetRatio
    width /= preset.zoom.coerceIn(1f, 4f)
    height /= preset.zoom.coerceIn(1f, 4f)
    val halfTravelX = (media.width - width) / 2f
    val halfTravelY = (media.height - height) / 2f
    val centerX = media.width / 2f + preset.panX.coerceIn(-1f, 1f) * halfTravelX
    val centerY = media.height / 2f + preset.panY.coerceIn(-1f, 1f) * halfTravelY
    return RectF(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}

private fun drawBackgroundImage(canvas: NativeCanvas, media: BackgroundMedia, preset: BackgroundPreset,
    viewWidth: Float, viewHeight: Float, paint: Paint) {
    if (viewWidth <= 0f || viewHeight <= 0f) return
    val crop = sourceCrop(media, preset, viewWidth / viewHeight)
    val sx = viewWidth / crop.width()
    val sy = viewHeight / crop.height()
    val factor = when (preset.fill) {
        BackgroundFill.COVER -> max(sx, sy)
        BackgroundFill.CONTAIN -> min(sx, sy)
        BackgroundFill.CENTER, BackgroundFill.TILE -> 1f
        BackgroundFill.STRETCH -> 1f
    }
    val width = if (preset.fill == BackgroundFill.STRETCH) viewWidth else crop.width() * factor
    val height = if (preset.fill == BackgroundFill.STRETCH) viewHeight else crop.height() * factor
    val bounds = RectF((viewWidth - width) / 2f, (viewHeight - height) / 2f,
        (viewWidth + width) / 2f, (viewHeight + height) / 2f)
    val saved = canvas.save()
    canvas.rotate(preset.rotation.coerceIn(-180f, 180f), viewWidth / 2f, viewHeight / 2f)
    if (preset.fill == BackgroundFill.TILE) {
        val countX = (viewWidth / width).toInt().coerceIn(1, 8) + 2
        val countY = (viewHeight / height).toInt().coerceIn(1, 8) + 2
        for (x in -1..countX) for (y in -1..countY) {
            val target = RectF(bounds).apply { offset(x * width, y * height) }
            drawOne(canvas, media, crop, target, paint)
        }
    } else drawOne(canvas, media, crop, bounds, paint)
    canvas.restoreToCount(saved)
}

private fun drawOne(canvas: NativeCanvas, media: BackgroundMedia, source: RectF, target: RectF, paint: Paint) {
    media.bitmap?.let { bitmap ->
        canvas.drawBitmap(bitmap, Rect(source.left.toInt(), source.top.toInt(), source.right.toInt(), source.bottom.toInt()), target, paint)
    } ?: media.movie?.let { movie ->
        @Suppress("DEPRECATION")
        movie.setTime((SystemClock.uptimeMillis() % movie.duration().coerceAtLeast(1000)).toInt())
        val saved = canvas.save()
        canvas.clipRect(target)
        canvas.translate(target.left - source.left * target.width() / source.width(), target.top - source.top * target.height() / source.height())
        canvas.scale(target.width() / source.width(), target.height() / source.height())
        @Suppress("DEPRECATION") movie.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(saved)
    }
}
