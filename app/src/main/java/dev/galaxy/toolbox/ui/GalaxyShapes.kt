package dev.galaxy.toolbox.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/** MIUIX outline with corner metadata required by Backdrop's real lens effect. */
internal class GalaxySquircleShape private constructor(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp) : this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius))

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline = Outline.Generic(Path().apply {
        addSquircleRect(size.width, size.height, minOf(topStart, topEnd, bottomEnd, bottomStart))
    })

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = GalaxySquircleShape(topStart, topEnd, bottomEnd, bottomStart)
}

internal object GalaxyShapes {
    val small: Shape @Composable get() = if (LocalAppStyle.current == AppStyle.MIUIX)
        GalaxySquircleShape(12.dp) else MaterialTheme.shapes.small
    val medium: Shape @Composable get() = if (LocalAppStyle.current == AppStyle.MIUIX)
        GalaxySquircleShape(18.dp) else MaterialTheme.shapes.medium
    val large: Shape @Composable get() = if (LocalAppStyle.current == AppStyle.MIUIX)
        GalaxySquircleShape(22.dp) else MaterialTheme.shapes.large
    val extraLarge: Shape @Composable get() = if (LocalAppStyle.current == AppStyle.MIUIX)
        GalaxySquircleShape(28.dp) else MaterialTheme.shapes.extraLarge
}
