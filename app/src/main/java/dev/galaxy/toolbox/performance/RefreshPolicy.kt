package dev.galaxy.toolbox.performance

data class RefreshMode(val id: Int, val width: Int, val height: Int, val hz: Float)
data class RefreshDecision(val hz: Float?, val modeId: Int?, val reason: String)

/** Hardware candidates only: Android still owns user settings, thermal and power limits. */
object RefreshPolicy {
    fun decide(current: RefreshMode?, modes: List<RefreshMode>, foreground: Boolean,
        powerSave: Boolean, thermalSevere: Boolean): RefreshDecision {
        if (!foreground) return RefreshDecision(null, null, "背景中 · 已釋放高刷新率請求")
        if (powerSave) return RefreshDecision(null, null, "省電模式 · 由系統調整")
        if (thermalSevere) return RefreshDecision(null, null, "系統熱壓力較高 · 由系統調整")
        if (current == null) return RefreshDecision(null, null, "未取得螢幕模式")
        val best = modes.filter { it.width == current.width && it.height == current.height && it.hz.isFinite() && it.hz > 0f }
            .maxByOrNull { it.hz } ?: current.takeIf { it.hz.isFinite() && it.hz > 0f }
        return RefreshDecision(best?.hz, best?.id, if (best == null) "無可用刷新率" else "已請求同解析度最高候選；實際由系統與你的設定決定")
    }
}

data class RefreshSnapshot(val requestedHz: Float? = null, val reportedHz: Float? = null,
    val width: Int? = null, val height: Int? = null, val reason: String = "等待前景螢幕資訊")
