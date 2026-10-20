/*
 * Adapted from Kyant's AndroidLiquidGlass catalog (Apache-2.0), tag 1.0.0:
 * commit eada9a8600142c1bd6a5c5fc143d06f599cdfaef
 * catalog/.../components/LiquidBottomTabs.kt and utils/DampedDragAnimation.kt.
 * Galaxy box modifications: theme, accessible single interactive row, gesture cancellation,
 * compact five-tab layout, capability fallback and failure handling. See THIRD_PARTY_NOTICES.md.
 */
package dev.galaxy.toolbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal val NavigationPosition = SemanticsPropertyKey<Float>("NavigationPosition")
internal var SemanticsPropertyReceiver.navigationPosition by NavigationPosition

@Composable internal fun GlassNavigation(pages: List<String>, selected: Int, onSelect: (Int) -> Unit,
    backdrop: Backdrop, blur: Boolean, motion: NavigationMotion? = null, backgroundVisible: Boolean = true) {
    if (pages.isEmpty()) return
    val selection = selected.coerceIn(pages.indices)
    val navigation = motion ?: rememberNavigationMotion(selection, pages.size)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val contentHeight = maxOf(56f, 28f + 16f * density.fontScale).dp
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val accent = Mint
    val tabsBackdrop = rememberLayerBackdrop()
    val bubbleBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    var widthPx by remember { mutableIntStateOf(0) }
    val sources = remember(pages.size) { List(pages.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }.any { it }
    val latestSelection by rememberUpdatedState(selection)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val moving by remember(navigation) { derivedStateOf { navigation.moving } }
    val active = pressed || moving
    val progress by animateFloatAsState(if (active) 1f else 0f, spring(.7f, 450f), label = "liquid-press")
    val scaleX by animateFloatAsState(if (blur && backgroundVisible) { if (active) 1.15f else 1.12f }
        else if (active) 1.08f else 1f, spring(.7f, 360f), label = "liquid-scale-x")
    val scaleY by animateFloatAsState(if (blur && backgroundVisible) { if (active) 1.35f else 1.32f }
        else if (active) 1.08f else 1f, spring(.7f, 360f), label = "liquid-scale-y")
    val speed by animateFloatAsState(navigation.velocity, spring(.72f, 440f), label = "liquid-velocity")
    DisposableEffect(widthPx, rtl, pages.size, contentHeight) {
        onDispose { navigation.cancelGesture() }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
        val overflow = if (blur && backgroundVisible) maxOf(20f, contentHeight.value * .28f).dp else 10.dp
        Box(Modifier.fillMaxWidth().height(contentHeight + 8.dp + overflow * 2).testTag("navigation-optical-container")) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(contentHeight + 8.dp)
                .testTag("navigation-panel").then(if (backgroundVisible) Modifier.liquidPanel(backdrop, blur) else Modifier))
            if (blur && backgroundVisible) {
                // Restore v0.6.7's panel + magnified glyph optical source, without its solid tint.
                Box(Modifier.matchParentSize().clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabsBackdrop)) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(contentHeight + 8.dp)
                        .liquidPanel(backdrop, true, shadow = false))
                    NavigationOpticalContents(pages, Modifier.align(Alignment.Center).fillMaxWidth()
                        .padding(horizontal = 4.dp).height(contentHeight), accent) { .85f + .15f * progress }
                }
            }
            Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp).height(contentHeight)
                .onSizeChanged { widthPx = it.width }.testTag("bottom-navigation")
                .semantics { navigationPosition = navigation.position }
                .pointerInput(pages.size, widthPx, rtl, contentHeight, navigation) {
                    if (widthPx <= 0) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        navigation.hold() // Freeze the current visual position, including an interrupted spring.
                        val slot = widthPx.toFloat() / pages.size
                        val direction = if (rtl) -1f else 1f
                        var dragged = false
                        var completed = false
                        try {
                            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume(); dragged = true
                                tracker.addPosition(change.uptimeMillis, change.position)
                                navigation.dragBy((change.position.x - down.position.x) / slot * direction,
                                    tracker.calculateVelocity().x / slot * direction)
                            }
                            if (start != null) completed = horizontalDrag(start.id) { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                navigation.dragBy(change.positionChange().x / slot * direction,
                                    tracker.calculateVelocity().x / slot * direction)
                                change.consume()
                            }
                        } finally {
                            val destination = navigation.release(commit = dragged && completed)
                            if (dragged && completed && destination != latestSelection) latestOnSelect(destination)
                        }
                    }
                }) {
                val bubble = @Composable {
                  if (widthPx > 0) {
                    val slot = widthPx.toFloat() / pages.size
                    Box(Modifier.align(AbsoluteAlignment.CenterLeft).width(with(density) { slot.toDp() }).height(contentHeight)
                        .graphicsLayer {
                            // Physical translation is separate from logical page order (RTL included).
                            val p = navigation.position.coerceIn(0f, pages.lastIndex.toFloat())
                            translationX = (if (rtl) pages.lastIndex - p else p) * slot
                        }.then(if (backgroundVisible) Modifier.liquidBubble(bubbleBackdrop, blur, dark, { if (blur) .85f + .15f * progress else progress },
                            { scaleX * (1f + (abs(speed) * .008f).coerceIn(0f, .015f)) },
                            { scaleY * (1f - (abs(speed) * .012f).coerceIn(0f, .05f)) })
                        else Modifier.graphicsLayer { this.scaleX = scaleX; this.scaleY = scaleY }
                            .border(.8.dp, accent.copy(alpha = .45f), RoundedCornerShape(50)))
                        .testTag("navigation-indicator"))
                  }
                }
                if (!blur || !backgroundVisible) bubble()
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    pages.forEachIndexed { index, name ->
                        NavigationTab(index, name, index == selection, sources[index], navigation, Modifier.weight(1f)) {
                            navigation.select(index); latestOnSelect(index)
                        }
                    }
                }
                if (blur && backgroundVisible) bubble()
            }
        }
    }
}

@Composable private fun NavigationOpticalContents(pages: List<String>, modifier: Modifier, tint: Color, progress: () -> Float) {
    Row(modifier) {
        pages.forEachIndexed { index, name ->
            Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer {
                val amount = 1f + .12f * progress(); scaleX = amount; scaleY = amount
            }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                NavigationGlyph(index, tint)
                Text(name, color = tint, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun RowScope.NavigationTab(index: Int, name: String, selected: Boolean,
    source: MutableInteractionSource, navigation: NavigationMotion, modifier: Modifier, onClick: () -> Unit) {
    val strength = navigation.emphasis(index)
    val tint = lerp(Muted, Mint, strength)
    Column(modifier.fillMaxHeight().selectable(selected, source, indication = null, role = Role.Tab, onClick = onClick)
        .testTag("navigation-page-$index").graphicsLayer {
            val amount = navigation.emphasis(index)
            scaleX = 1f + .035f * amount; scaleY = 1f + .035f * amount
        }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
        NavigationGlyph(index, tint)
        Text(name, color = tint, style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Modifier.liquidPanel(backdrop: Backdrop, enabled: Boolean, shadow: Boolean = true): Modifier {
    if (!enabled) return background(Panel, RoundedCornerShape(50))
    val fallback = Panel
    val failure = LocalGlass.current.onFailure
    val failed = remember(backdrop) { AtomicBoolean(false) }
    return drawBackdrop(backdrop, shape = { RoundedCornerShape(50) }, effects = {
        if (!failed.get()) try { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) }
        catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); failure() }
    }, highlight = { Highlight(width = .7.dp, alpha = .65f) },
        shadow = if (shadow) ({ Shadow(radius = 16.dp, alpha = .7f) }) else null,
        onDrawSurface = {
            // No solid fill, gradient or tint behind the glass navigation.
            if (failed.get()) drawRect(fallback)

        })
}

@Composable private fun Modifier.liquidBubble(backdrop: Backdrop, enabled: Boolean, dark: Boolean,
    progress: () -> Float, scaleX: () -> Float, scaleY: () -> Float): Modifier {
    val shape = RoundedCornerShape(50)
    val fallback = Panel
    if (!enabled) return graphicsLayer { this.scaleX = scaleX(); this.scaleY = scaleY() }
        .background((if (dark) Color.White else Color.Black).copy(alpha = .1f), shape)
    val failure = LocalGlass.current.onFailure
    val failed = remember(backdrop) { AtomicBoolean(false) }
    return drawBackdrop(backdrop, shape = { shape }, effects = {
        if (!failed.get()) try { lens(10.dp.toPx() * progress(), 14.dp.toPx() * progress(), chromaticAberration = true) }
        catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); failure() }
    }, highlight = { Highlight(width = .6.dp, alpha = progress()) },
        shadow = { Shadow(radius = 16.dp, alpha = progress()) },
        innerShadow = { InnerShadow(radius = 8.dp * progress(), alpha = progress()) },
        layerBlock = { this.scaleX = scaleX(); this.scaleY = scaleY() },
        onDrawSurface = {
            if (failed.get()) drawRect(fallback)
            else {
                drawRect((if (dark) Color.White else Color.Black).copy(alpha = .1f * (1f - progress())))
                drawRect(Color.Black.copy(alpha = .03f * progress()))
            }
        })
}

