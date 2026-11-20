package dev.galaxy.toolbox.ui

import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator

@Composable
internal fun GalaxyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixLinearProgressIndicator(modifier = modifier, progress = progress?.invoke())
    } else if (progress == null) {
        MaterialLinearProgressIndicator(modifier = modifier)
    } else {
        MaterialLinearProgressIndicator(progress = progress, modifier = modifier)
    }
}
