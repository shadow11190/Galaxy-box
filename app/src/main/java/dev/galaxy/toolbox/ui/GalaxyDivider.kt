package dev.galaxy.toolbox.ui

import androidx.compose.material3.HorizontalDivider as MaterialDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.HorizontalDivider as MiuixDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GalaxyDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Dp.Hairline,
    color: Color = Color.Unspecified,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixDivider(modifier = modifier, thickness = thickness,
            color = if (color == Color.Unspecified) MiuixTheme.colorScheme.dividerLine else color)
    } else {
        MaterialDivider(modifier = modifier, thickness = thickness,
            color = if (color == Color.Unspecified) GalaxyPalette.onSurfaceVariant.copy(alpha = .12f) else color)
    }
}
