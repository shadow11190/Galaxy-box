package dev.galaxy.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

@Composable
internal fun GalaxySurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Panel,
    content: @Composable () -> Unit,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        val effectiveShape = if (shape == RectangleShape) RectangleShape else GalaxyShapes.extraLarge
        Box(modifier.clip(effectiveShape).background(color)) { content() }
    } else {
        MaterialSurface(modifier = modifier, shape = shape, color = color, content = content)
    }
}
