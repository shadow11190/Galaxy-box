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
import dev.galaxy.toolbox.R

/** No background, frame, shadow, or placeholder. Every mapped asset has transparency. */
@Composable internal fun BrandLogo(mark: String) {
    val resource = when (mark) {
        "Xiaomi" -> R.drawable.brand_xiaomi
        "Redmi" -> R.drawable.brand_redmi
        "POCO" -> R.drawable.brand_poco
        "Black Shark" -> R.drawable.brand_black_shark
        "OPPO" -> R.drawable.brand_oppo
        "OnePlus" -> R.drawable.brand_oneplus
        "realme" -> R.drawable.brand_realme
        "vivo" -> R.drawable.brand_vivo
        "iQOO" -> R.drawable.brand_iqoo
        "HUAWEI" -> R.drawable.brand_huawei
        "HONOR" -> R.drawable.brand_honor
        "ZTE" -> R.drawable.brand_zte
        "nubia" -> R.drawable.brand_nubia
        "Meizu" -> R.drawable.brand_meizu
        "Lenovo" -> R.drawable.brand_lenovo
        "Motorola" -> R.drawable.brand_motorola
        "ASUS" -> R.drawable.brand_asus
        "ROG Phone" -> R.drawable.brand_rog_phone
        "HTC" -> R.drawable.brand_htc
        "SAMSUNG" -> R.drawable.brand_samsung
        "LG" -> R.drawable.brand_lg
        "Sony" -> R.drawable.brand_sony
        "Sharp" -> R.drawable.brand_sharp
        "Kyocera" -> R.drawable.brand_kyocera
        "Fujitsu" -> R.drawable.brand_fujitsu
        "Rakuten Mobile" -> R.drawable.brand_rakuten_mobile
        "Google Pixel" -> R.drawable.brand_google_pixel
        "Razer Phone" -> R.drawable.brand_razer_phone
        "Nothing" -> R.drawable.brand_nothing
        "CMF by Nothing" -> R.drawable.brand_cmf_by_nothing
        "Fairphone" -> R.drawable.brand_fairphone
        "Nokia" -> R.drawable.brand_nokia
        else -> null
    } ?: return
    Image(painterResource(resource), contentDescription = "$mark 品牌標誌",
        modifier = Modifier.width(96.dp).height(52.dp), contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = .82f)))
}
