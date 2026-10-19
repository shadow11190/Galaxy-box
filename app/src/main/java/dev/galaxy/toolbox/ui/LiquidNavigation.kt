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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable internal fun GlassNavigation(pages: List<String>, selected: Int, onSelect: (Int) -> Unit,
    backdrop: Backdrop, blur: Boolean) {
    if (pages.isEmpty()) return
    val selection = selected.coerceIn(pages.indices)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val contentHeight = maxOf(56f, 28f + 16f * density.fontScale).dp
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val surface = (if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = .4f)
    val accent = Mint
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selection.toFloat()) }
    var dragSpeed by remember { mutableFloatStateOf(0f) }
    var displacement by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableStateOf(false) }
    var previousSelection by remember { mutableIntStateOf(selection) }
    val sources = remember(pages.size) { List(pages.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }.any { it }
    val latestSelection by rememberUpdatedState(selection)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val tracker = remember { VelocityTracker() }
    val position by animateFloatAsState(if (dragging) dragPosition else selection.toFloat(),
        spring(1f, 1000f, .001f), label = "liquid-position")
    val active = pressed || dragging || pulse
    val progress by animateFloatAsState(if (active) 1f else 0f, spring(1f, 1000f), label = "liquid-press")
    val targetScaleX = if (blur) { if (active) 1.2f else 1.06f } else if (active) 1.08f else 1f
    val targetScaleY = if (blur) { if (active) 1.4f else 1.22f } else if (active) 1.08f else 1f
    val scaleX by animateFloatAsState(targetScaleX, spring(.6f, 250f), label = "liquid-scale-x")
    val scaleY by animateFloatAsState(targetScaleY, spring(.7f, 250f), label = "liquid-scale-y")
    val speed by animateFloatAsState(if (dragging) dragSpeed else 0f, spring(.5f, 300f), label = "liquid-velocity")
    val drift by animateFloatAsState(if (dragging && widthPx > 0)
        (displacement / widthPx).coerceIn(-1f, 1f) * with(density) { 4.dp.toPx() } else 0f,
        spring(1f, 300f), label = "liquid-panel-drift")
    val preview = if (dragging) dragPosition.roundToInt().coerceIn(pages.indices) else selection
    val tabsBackdrop = rememberLayerBackdrop()
    val bubbleBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    LaunchedEffect(selection) {
        dragging = false; dragSpeed = 0f
        if (previousSelection != selection) {
            previousSelection = selection
            pulse = true
            try { delay(140) } finally { pulse = false }
        }
    }
    LaunchedEffect(widthPx, rtl, pages.size, contentHeight) { dragging = false; dragSpeed = 0f; displacement = 0f }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
        // Panel, capture source and bubble are siblings. No capsule-shaped ancestor clips overflow.
        val overflow = if (blur) maxOf(20f, contentHeight.value * .28f).dp else 10.dp
        Box(Modifier.fillMaxWidth().height(contentHeight + 8.dp + overflow * 2)
            .graphicsLayer { translationX = drift; clip = false }.testTag("navigation-optical-container")) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(contentHeight + 8.dp)
                .testTag("navigation-panel").liquidPanel(backdrop, blur, surface, { progress }, { (position + .5f) / pages.size }))
            if (blur) {
                // Capture includes the overflow area so enlarged glyphs and labels keep their full extent.
                Box(Modifier.matchParentSize().clearAndSetSemantics {}.alpha(0f).layerBackdrop(tabsBackdrop)) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(contentHeight + 8.dp)
                        .liquidPanel(backdrop, true, surface, { progress }, { (position + .5f) / pages.size }, shadow = false))
                    NavigationContents(pages, selection, null,
                        Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp).height(contentHeight),
                        color = { accent }, scale = { 1f + .12f * progress }, onSelect = {})
                }
            }
            Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 4.dp).height(contentHeight).onSizeChanged { widthPx = it.width }
                .testTag("bottom-navigation").pointerInput(pages.size, widthPx, rtl, contentHeight) {
                    detectHorizontalDragGestures(
                        onDragStart = { down ->
                            tracker.resetTracking(); displacement = 0f
                            dragPosition = NavigationDrag.positionAt(down.x, widthPx.toFloat(), pages.size, rtl)
                            dragging = true
                        },
                        onHorizontalDrag = { change, delta ->
                            if (dragging) {
                                change.consume(); displacement += delta
                                tracker.addPosition(change.uptimeMillis, change.position)
                                dragSpeed = tracker.calculateVelocity().x / widthPx.coerceAtLeast(1)
                                dragPosition = NavigationDrag.positionAt(change.position.x, widthPx.toFloat(), pages.size, rtl)
                            }
                        },
                        onDragEnd = {
                            if (dragging) {
                                val target = dragPosition.roundToInt().coerceIn(pages.indices)
                                dragging = false; dragSpeed = 0f
                                if (target != latestSelection) latestOnSelect(target)
                            }
                        }, onDragCancel = { dragging = false; dragSpeed = 0f }
                    )
                }) {
                val bubble = @Composable {
                    if (widthPx > 0) {
                        val slot = widthPx.toFloat() / pages.size
                        Box(Modifier.offset { IntOffset((position * slot).roundToInt(), 0) }
                            .width(with(density) { slot.toDp() }).height(contentHeight)
                            .liquidBubble(bubbleBackdrop, blur, dark, { progress },
                                { scaleX / (1f - (abs(speed) * .075f).coerceIn(0f, .2f)) },
                                { scaleY * (1f - (abs(speed) * .025f).coerceIn(0f, .08f)) })
                            .testTag("navigation-indicator"))
                    }
                }
                if (!blur) bubble()
                NavigationContents(pages, selection, sources, Modifier.fillMaxSize(),
                    color = { index -> if (!blur && preview == index) accent else Muted }, scale = { 1f }) { index ->
                    dragging = false; dragSpeed = 0f; latestOnSelect(index)
                }
                if (blur) bubble()
            }
        }
    }
}

@Composable private fun NavigationContents(pages: List<String>, selected: Int, sources: List<MutableInteractionSource>?,
    modifier: Modifier, color: @Composable (Int) -> Color, scale: () -> Float, onSelect: (Int) -> Unit) {
    Row(modifier.then(if (sources != null) Modifier.selectableGroup() else Modifier)) {
        pages.forEachIndexed { index, name ->
            val tint = color(index)
            Column(Modifier.weight(1f).fillMaxHeight().then(if (sources == null) Modifier else
                Modifier.selectable(selected == index, sources[index], indication = null, role = Role.Tab,
                    onClick = { onSelect(index) }).testTag("navigation-page-$index"))
                .graphicsLayer { scaleX = scale(); scaleY = scale() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                NavigationGlyph(index, tint)
                Text(name, color = tint, style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun Modifier.liquidPanel(backdrop: Backdrop, enabled: Boolean, tint: Color,
    progress: () -> Float, position: () -> Float, shadow: Boolean = true): Modifier {
    if (!enabled) return background(Panel, RoundedCornerShape(50))
    val failure = LocalGlass.current.onFailure
    val failed = remember(backdrop) { AtomicBoolean(false) }
    return drawBackdrop(backdrop, shape = { RoundedCornerShape(50) }, effects = {
        if (!failed.get()) try { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) }
        catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); failure() }
    }, highlight = { Highlight(width = .7.dp, alpha = .65f) },
        shadow = if (shadow) ({ Shadow(radius = 16.dp, alpha = .7f) }) else null,
        layerBlock = { val amount = 1f + 12.dp.toPx() / size.width.coerceAtLeast(1f) * progress(); scaleX = amount; scaleY = amount },
        onDrawSurface = {
            drawRect(tint.copy(alpha = if (failed.get()) 1f else tint.alpha))
            if (progress() > 0f) drawRect(Brush.radialGradient(
                listOf(Color.White.copy(alpha = .13f * progress()), Color.Transparent),
                center = Offset(size.width * position(), size.height / 2f), radius = size.height * 1.5f))
        })
}

@Composable private fun Modifier.liquidBubble(backdrop: Backdrop, enabled: Boolean, dark: Boolean,
    progress: () -> Float, scaleX: () -> Float, scaleY: () -> Float): Modifier {
    val shape = RoundedCornerShape(50)
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
            drawRect((if (dark) Color.White else Color.Black).copy(alpha = .1f * (1f - progress())))
            drawRect(Color.Black.copy(alpha = .03f * progress()))
        })
}
