package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Draw behind the transparent navigation; end padding keeps the final action reachable. */
@Composable internal fun AppScrollContent(insets: PaddingValues, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    val miuix = LocalAppStyle.current == AppStyle.MIUIX
    val side = 16.dp
    val gap = if (miuix) 12.dp else 10.dp
    val scrollConnection = LocalAppNestedScrollConnection.current
    BoxWithConstraints(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).imePadding()) {
        LazyColumn(Modifier.width(maxWidth.coerceAtMost(840.dp)).fillMaxHeight()
            .align(Alignment.TopCenter).clipToBounds().then(if (scrollConnection != null)
                Modifier.nestedScroll(scrollConnection) else Modifier).then(modifier).testTag("app-scroll-content"),
            state = state, contentPadding = PaddingValues(start = side, end = side,
                top = insets.calculateTopPadding() + 12.dp,
                bottom = 16.dp + insets.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}
