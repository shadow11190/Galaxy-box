package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Visual checks for the shared card, controls, top bar, bottom bar and page host. */
@Composable private fun ComponentPreview(style: AppStyle, dark: Boolean) {
    GalaxyBoxTheme(dark = dark, style = style, themeMode = if (dark) "dark" else "light") {
        val backdrops = rememberAppBackdrops()
        val pages = listOf("總覽", "硬體", "電池", "更新", "檢測")
        val motion = rememberNavigationMotion(0, pages.size)
        AppScaffold(pages, 0, {}, backdrops.controls, motion, true,
            rememberLazyListState(), {}, {}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLargeTitle("總覽", rememberLazyListState())
                AppCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppListItem("目前裝置", supporting = "Galaxy S26 Ultra")
                        AppSwitchRow("降低透明度", false, {})
                        AppButton("檢視電池", {})
                    }
                }
            }
        }
    }
}

@Preview(name = "Liquid · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun LiquidLightPreview() = ComponentPreview(AppStyle.LIQUID_GLASS, false)

@Preview(name = "Liquid · Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun LiquidDarkPreview() = ComponentPreview(AppStyle.LIQUID_GLASS, true)

@Preview(name = "MIUIX · Light", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun MiuixLightPreview() = ComponentPreview(AppStyle.MIUIX, false)

@Preview(name = "MIUIX · Dark", showBackground = true, widthDp = 393, heightDp = 852)
@Composable private fun MiuixDarkPreview() = ComponentPreview(AppStyle.MIUIX, true)

@Preview(name = "Compact · 320 dp", showBackground = true, widthDp = 320, heightDp = 700)
@Composable private fun CompactPreview() = ComponentPreview(AppStyle.LIQUID_GLASS, false)

@Preview(name = "Unfolded · 673 dp", showBackground = true, widthDp = 673, heightDp = 841)
@Composable private fun UnfoldedPreview() = ComponentPreview(AppStyle.LIQUID_GLASS, false)

@Preview(name = "Wide landscape · 1000 dp", showBackground = true, widthDp = 1000, heightDp = 600)
@Composable private fun WidePreview() = ComponentPreview(AppStyle.MIUIX, true)

@Preview(name = "Large text · 200%", showBackground = true, widthDp = 393, heightDp = 852, fontScale = 2f)
@Composable private fun LargeTextPreview() = ComponentPreview(AppStyle.LIQUID_GLASS, false)

@Composable private fun DialogPreview(style: AppStyle, dark: Boolean) {
    GalaxyBoxTheme(dark = dark, style = style, themeMode = if (dark) "dark" else "light") {
        AppDialog(true, "確認設定", {}) {
            AppListItem("背景模式", supporting = "使用預設背景")
            AppButton("完成", {})
        }
    }
}

@Preview(name = "Dialog · Liquid · Light", showBackground = true, widthDp = 393, heightDp = 600)
@Composable private fun DialogLiquidLightPreview() = DialogPreview(AppStyle.LIQUID_GLASS, false)

@Preview(name = "Dialog · Liquid · Dark", showBackground = true, widthDp = 393, heightDp = 600)
@Composable private fun DialogLiquidDarkPreview() = DialogPreview(AppStyle.LIQUID_GLASS, true)

@Preview(name = "Dialog · MIUIX · Light", showBackground = true, widthDp = 393, heightDp = 600)
@Composable private fun DialogMiuixLightPreview() = DialogPreview(AppStyle.MIUIX, false)

@Preview(name = "Dialog · MIUIX · Dark", showBackground = true, widthDp = 393, heightDp = 600)
@Composable private fun DialogMiuixDarkPreview() = DialogPreview(AppStyle.MIUIX, true)
