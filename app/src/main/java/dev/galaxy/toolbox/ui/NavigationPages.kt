package dev.galaxy.toolbox.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.floor

/** At most two live page layers. Per-frame position is read only in graphicsLayer. */
@Composable internal fun NavigationPages(motion: NavigationMotion, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moving by remember(motion) { derivedStateOf { motion.moving } }
    val visible by remember(motion) { derivedStateOf {
        val p = motion.position.coerceIn(0f, (motion.count - 1).toFloat())
        listOf(floor(p).toInt(), ceil(p).toInt()).distinct()
    } }
    Box(modifier.fillMaxSize().clipToBounds().testTag("navigation-pages")) {
        visible.forEach { page -> key(page) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val relative = page - motion.position.coerceIn(0f, (motion.count - 1).toFloat())
                translationX = relative * size.width * if (rtl) -1f else 1f
            }.then(if (page == motion.target) Modifier else Modifier.clearAndSetSemantics {})) {
                holder.SaveableStateProvider(page) { content(page) }
            }
        } }
        // Never activate a partially sliding page's buttons. Navigation itself remains available.
        if (moving) Box(Modifier.matchParentSize().clearAndSetSemantics {}.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } }
                while (event.changes.any { it.pressed })
            }
        })
    }
}
