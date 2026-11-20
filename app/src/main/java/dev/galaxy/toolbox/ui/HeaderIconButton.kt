package dev.galaxy.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class HeaderIcon { SETTINGS, ABOUT, CLOSE }

/** One small visual control with a full 48 dp touch target in either theme. */
@Composable internal fun HeaderIconButton(icon: HeaderIcon, label: String,
    grouped: Boolean = false, onClick: () -> Unit) {
    val language = LocalAppLanguage.current
    val description = AppText.translate(label, language)
    val ink = GalaxyPalette.onSurface
    val border = GalaxyPalette.onSurfaceVariant.copy(alpha = .16f)
    val shape = RoundedCornerShape(50)
    val glass = LocalGlass.current.let { it.enabled && it.backdrop != null }
    Box(Modifier.size(48.dp)
        .then(if (grouped) Modifier else if (glass) Modifier.galaxyGlass(shape, floating = true)
            else Modifier.background(Panel, shape))
        .then(if (grouped) Modifier else Modifier.border(.8.dp, border, shape))
        .clip(shape)
        .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val u = size.minDimension / 24f
            val center = Offset(size.width / 2f, size.height / 2f)
            val line = Stroke(1.9f * u)
            when (icon) {
                HeaderIcon.SETTINGS -> {
                    drawCircle(ink, radius = 7.1f * u, center = center, style = line)
                    drawCircle(ink, radius = 2.5f * u, center = center, style = line)
                    repeat(8) { index ->
                        val angle = index * PI / 4.0
                        val dx = cos(angle).toFloat()
                        val dy = sin(angle).toFloat()
                        drawLine(ink, Offset(center.x + dx * 8.6f * u, center.y + dy * 8.6f * u),
                            Offset(center.x + dx * 11f * u, center.y + dy * 11f * u), 1.9f * u)
                    }
                }
                HeaderIcon.ABOUT -> {
                    drawCircle(ink, radius = 10f * u, center = center, style = line)
                    drawCircle(ink, radius = 1.25f * u,
                        center = Offset(center.x, center.y - 5.1f * u))
                    drawLine(ink, Offset(center.x, center.y - 1.1f * u),
                        Offset(center.x, center.y + 5.7f * u), 2f * u)
                }
                HeaderIcon.CLOSE -> {
                    drawLine(ink, Offset(center.x - 6f * u, center.y - 6f * u),
                        Offset(center.x + 6f * u, center.y + 6f * u), 2f * u)
                    drawLine(ink, Offset(center.x + 6f * u, center.y - 6f * u),
                        Offset(center.x - 6f * u, center.y + 6f * u), 2f * u)
                }
            }
        }
    }
}
