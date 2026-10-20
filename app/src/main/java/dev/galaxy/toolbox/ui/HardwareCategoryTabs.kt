package dev.galaxy.toolbox.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.galaxy.toolbox.model.HardwareCategory
import kotlinx.coroutines.flow.first

/** State belongs to ToolboxApp, outside category-dependent LazyColumn items and page layers. */
@Composable internal fun HardwareCategoryTabs(category: HardwareCategory, onCategory: (HardwareCategory) -> Unit, state: LazyListState) {
    LaunchedEffect(category) {
        snapshotFlow { state.layoutInfo.viewportEndOffset > 0 }.first { it }
        val layout = state.layoutInfo
        val selected = layout.visibleItemsInfo.firstOrNull { it.key == category.name }
        if (selected == null) state.animateScrollToItem(category.ordinal)
        else {
            // Keep the user's offset when the selection is already visible; only reveal a clipped tab.
            val delta = when {
                selected.offset < layout.viewportStartOffset -> selected.offset - layout.viewportStartOffset
                selected.offset + selected.size > layout.viewportEndOffset -> selected.offset + selected.size - layout.viewportEndOffset
                else -> 0
            }
            if (delta != 0) state.animateScrollBy(delta.toFloat())
        }
    }
    LazyRow(Modifier.fillMaxWidth().testTag("hardware-category-tabs"), state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(HardwareCategory.entries, key = { it.name }) { item ->
            FilterChip(selected = item == category, onClick = { onCategory(item) }, label = { Text(item.title) })
        }
    }
}
