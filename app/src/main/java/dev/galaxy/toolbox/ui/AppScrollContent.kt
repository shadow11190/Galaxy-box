package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Reserve the entire bottom bar area. Interactive content never enters its capture region. */
@Composable internal fun AppScrollContent(insets: PaddingValues, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        .clipToBounds().then(modifier).testTag("app-scroll-content"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
