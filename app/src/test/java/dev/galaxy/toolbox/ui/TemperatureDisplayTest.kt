package dev.galaxy.toolbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureDisplayTest {
    @Test fun simplifiedSettingsUseMainlandTermsOnAllSupportedAndroidVersions() {
        assertEquals("设置", AppText.translate("設定", AppLanguage.ZH_CN))
        assertEquals("电池温度", AppText.translate("電池溫度", AppLanguage.ZH_CN))
    }

    @Test fun convertsOnlyMeasuredCelsiusValues() {
        assertEquals("最小 32.0 °F · 最大 212.0 °F",
            TemperatureText.display("最小 0.0 °C · 最大 100.0 °C", TemperatureDisplayUnit.FAHRENHEIT))
        assertEquals("未知 · °C", TemperatureText.display("未知 · °C", TemperatureDisplayUnit.FAHRENHEIT))
        assertEquals("36.5 °C", TemperatureText.display("36.5 °C", TemperatureDisplayUnit.CELSIUS))
    }

    @Test fun chartUsesConvertedNumbersWithoutChangingSourceValues() {
        assertEquals(98.6, TemperatureText.displayNumber(37.0, TemperatureDisplayUnit.FAHRENHEIT), 0.0001)
        assertEquals(37.0, TemperatureText.displayNumber(37.0, TemperatureDisplayUnit.CELSIUS), 0.0001)
    }

    @Test fun englishTemplatesPreserveLiveMeasurements() {
        assertEquals("37.9 °C", AppText.translate("37.9 °C", AppLanguage.EN))
        assertEquals("Battery temperature 37.9 °C", AppText.translate("電池溫度 37.9 °C", AppLanguage.EN))
        assertEquals("8.1 / 10.8 GiB used", AppText.translate("8.1 / 10.8 GiB 已用", AppLanguage.EN))
        assertEquals("8 / 8 cores readable", AppText.translate("8 / 8 個核心可讀", AppLanguage.EN))
        assertEquals("v0.7.2 · Technical preview", AppText.translate("v0.7.2 · 技術預覽版", AppLanguage.EN))
        assertEquals("Found 3 battery readings", AppText.translate("已取得 3 項電池資料", AppLanguage.EN))
        assertEquals("Y-axis 0–100% · 60 samples", AppText.translate("縱軸 0–100% · 60 筆", AppLanguage.EN))
        assertEquals("Min 32.0 °F · Max 212.0 °F", TemperatureText.display(
            AppText.translate("最小 0.0 °C · 最大 100.0 °C", AppLanguage.EN), TemperatureDisplayUnit.FAHRENHEIT))
    }
}
