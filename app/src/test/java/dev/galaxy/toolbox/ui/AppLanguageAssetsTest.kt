package dev.galaxy.toolbox.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLanguageAssetsTest {
    @Test fun bundledTranslationsLoadForExistingScreens() {
        AppText.install(ApplicationProvider.getApplicationContext<Context>())
        assertEquals("Current device", AppText.translate("目前裝置", AppLanguage.EN))
        assertEquals("Battery temperature", AppText.translate("電池溫度", AppLanguage.EN))
        assertEquals("设置", AppText.translate("設定", AppLanguage.ZH_CN))
        assertEquals("内存", AppText.translate("記憶體", AppLanguage.ZH_CN))
        assertEquals("存储空间", AppText.translate("儲存空間", AppLanguage.ZH_CN))
        assertEquals("目前裝置", AppText.translate("目前裝置", AppLanguage.ZH_TW))
        assertEquals("System is still using SBC", AppText.translate("系統目前仍使用 SBC", AppLanguage.EN))
        assertEquals("Headset battery (system reported) 80%", AppText.translate("耳機電量（系統回報）80%", AppLanguage.EN))
        assertEquals("Y-axis: °C · X-axis: time ago. Failed or paused sampling stays blank, not zero.",
            AppText.translate("縱軸：°C · 橫軸：距今時間。失敗或停止輪詢的時段留空，不補 0。", AppLanguage.EN))
    }
    @Test fun switchingToSimplifiedCanRenderTheFullBundledPhraseCorpus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppText.install(context)
        val phrases = context.assets.open("i18n/zh-CN-generated.json").bufferedReader().use { JSONObject(it.readText()) }
        org.junit.Assert.assertTrue(phrases.length() > 1000)
        phrases.keys().forEach { source -> AppText.translate(source, AppLanguage.ZH_CN) }
        assertEquals("电池温度 37.9 °C", AppText.translate("電池溫度 37.9 °C", AppLanguage.ZH_CN))
        assertEquals("内存与存储空间", AppText.translate("記憶體與儲存空間", AppLanguage.ZH_CN))
        assertEquals("小组件已更新", AppText.translate("小工具已更新", AppLanguage.ZH_CN))
    }
    @Test fun bundledEnglishPhrasesHaveNoRemainingChineseText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppText.install(context)
        val phrases = context.assets.open("i18n/en-generated.json").bufferedReader().use { JSONObject(it.readText()) }
        org.junit.Assert.assertTrue(phrases.length() > 500)
        phrases.keys().forEach { source ->
            val translated = AppText.translate(source, AppLanguage.EN)
            org.junit.Assert.assertFalse("Untranslated: $source", Regex("[\\u3400-\\u9FFF]").containsMatchIn(translated))
        }
    }
}
