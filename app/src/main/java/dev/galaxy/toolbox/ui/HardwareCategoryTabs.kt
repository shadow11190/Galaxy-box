package dev.galaxy.toolbox.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
    val shape = if (LocalAppStyle.current == AppStyle.MIUIX) GalaxySquircleShape(28.dp)
        else RoundedCornerShape(28.dp)
    val glass = LocalGlassTier.current != GlassTier.OFF
    val panel = if (glass) Modifier.galaxyGlass(shape, backdrop = LocalCardBackdrop.current, compact = true)
        else Modifier.background(Panel, shape)
    LazyRow(Modifier.fillMaxWidth().then(panel).padding(4.dp)
        .selectableGroup().testTag("hardware-category-tabs"),
        state = state, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        items(HardwareCategory.entries, key = { it.name }, contentType = { "hardware-category" }) { item ->
            val selected = item == category
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) .96f else 1f,
                spring(dampingRatio = .8f, stiffness = 420f), label = "hardware-tab-press")
            val tabShape = if (LocalAppStyle.current == AppStyle.MIUIX) GalaxySquircleShape(24.dp)
                else RoundedCornerShape(24.dp)
            val fill = if (selected) Modifier.background(Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (GalaxyPalette.background.red < .4f) .25f else .66f),
                Mint.copy(alpha = .15f))), tabShape)
                .border(.7.dp, Color.White.copy(alpha = .38f), tabShape) else Modifier
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(fill)
                .heightIn(min = 48.dp)
                .selectable(selected = selected, interactionSource = interaction, indication = null,
                    role = Role.Tab, onClick = { onCategory(item) })
                .padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                GalaxyText(item.title, color = if (selected) GalaxyPalette.onSurface else Muted,
                    style = GalaxyTypography.labelLarge)
            }
        }
    }
}
