package dev.galaxy.toolbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.data.BrandMarks

/** No background, frame, shadow, or placeholder. Every mapped asset has transparency. */
@Composable internal fun BrandLogo(mark: String) {
    val resource = BrandMarks.drawableResource(mark) ?: return
    val description = AppText.translate("$mark 品牌標誌", LocalAppLanguage.current)
    Image(painterResource(resource), contentDescription = description,
        modifier = Modifier.width(96.dp).height(52.dp), contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(GalaxyPalette.onSurface.copy(alpha = .82f)))
}
