package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A width-driven alternative to the compact glass bar; the page content keeps its own scroll state. */
@Composable internal fun AdaptiveNavigationRail(
    pages: List<String>, selected: Int, onSelect: (Int) -> Unit,
) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(horizontal = 8.dp).verticalScroll(rememberScrollState()).selectableGroup(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        pages.forEachIndexed { index, title ->
            val active = selected == index
            Column(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .then(if (active && !LocalGlass.current.enabled) Modifier.background(
                        GalaxyPalette.primary.copy(alpha = .17f), GalaxyShapes.medium) else Modifier)
                    .galaxyGlass(shape = GalaxyShapes.medium, compact = true, enabled = active && LocalGlass.current.enabled)
                    .selectable(selected = active, onClick = { onSelect(index) }, role = Role.Tab)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NavigationGlyph(index, if (active) Mint else Muted)
                GalaxyText(title, style = GalaxyTypography.labelSmall,
                    color = if (active) Mint else Muted)
            }
        }
    }
}
