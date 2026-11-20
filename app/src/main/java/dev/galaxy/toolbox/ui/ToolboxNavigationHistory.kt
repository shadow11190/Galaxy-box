package dev.galaxy.toolbox.ui

import dev.galaxy.toolbox.model.HardwareCategory

internal data class ToolboxRoute(val page: Int, val hardwareCategory: HardwareCategory) {
    fun savedKey(): String = "$page/${hardwareCategory.name}"

    companion object {
        fun fromSavedKey(key: String): ToolboxRoute? {
            val parts = key.split('/', limit = 2)
            val page = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..4 } ?: return null
            val category = HardwareCategory.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: return null
            return ToolboxRoute(page, category)
        }
    }
}

internal object ToolboxNavigationHistory {
    private const val MAX_ENTRIES = 32

    fun visit(history: List<String>, current: ToolboxRoute, target: ToolboxRoute): List<String> = when {
        target == current -> history
        target.page == 0 -> emptyList()
        else -> (history + current.savedKey()).takeLast(MAX_ENTRIES)
    }

    fun previous(history: List<String>, current: ToolboxRoute): Pair<ToolboxRoute, List<String>> {
        val previous = history.lastOrNull()?.let(ToolboxRoute::fromSavedKey)
        return if (previous != null) previous to history.dropLast(1)
            else ToolboxRoute(0, current.hardwareCategory) to emptyList()
    }
}
