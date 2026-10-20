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
    LazyColumn(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .clipToBounds().then(modifier).testTag("app-scroll-content"),
        state = state, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + insets.calculateBottomPadding()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
