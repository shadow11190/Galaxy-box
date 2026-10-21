package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Draw behind the transparent navigation; end padding keeps the final action reachable. */
@Composable internal fun AppScrollContent(insets: PaddingValues, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    val miuix = LocalVisualStyle.current == GalaxyVisualStyle.MIUIX
    val side = if (miuix) 16.dp else 20.dp
    val gap = if (miuix) 12.dp else 14.dp
    LazyColumn(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .clipToBounds().then(modifier).testTag("app-scroll-content"),
        state = state, contentPadding = PaddingValues(start = side, end = side, top = if (miuix) 12.dp else 20.dp,
            bottom = 20.dp + insets.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}
