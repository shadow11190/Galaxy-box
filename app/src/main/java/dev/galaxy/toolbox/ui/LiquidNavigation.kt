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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.graphics.Brush
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
    val dark = GalaxyPalette.background.red < .3f
    val accent = Mint
    val miuix = LocalAppStyle.current == AppStyle.MIUIX
    var widthPx by remember { mutableIntStateOf(0) }
    val sources = remember(pages.size) { List(pages.size) { MutableInteractionSource() } }
    val pressed = sources.map { it.collectIsPressedAsState().value }.any { it }
    val latestSelection by rememberUpdatedState(selection)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val moving by remember(navigation) { derivedStateOf { navigation.moving } }
    val active = pressed || moving
    val tokens = LocalAppDesignTokens.current
    val indicatorShape = RoundedCornerShape(50)
    val scaleXState = animateFloatAsState(if (active) 1.15f else 1.07f,
        spring(tokens.springDamping, tokens.springStiffness), label = "liquid-scale-x")
    val scaleYState = animateFloatAsState(if (active) 1.32f else 1.24f,
        spring(tokens.springDamping, tokens.springStiffness), label = "liquid-scale-y")
    val speedState = animateFloatAsState(navigation.velocity, spring(.72f, 440f), label = "liquid-velocity")
    val tier = LocalGlassTier.current
    val bubbleFailed = remember(backdrop) { AtomicBoolean(false) }
    val bubbleFailure = LocalGlass.current.onFailure
    DisposableEffect(widthPx, rtl, pages.size, contentHeight) {
        onDispose { navigation.cancelGesture() }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp)) {
        val overflow = if (blur) 12.dp else 8.dp
        Box(Modifier.fillMaxWidth().height(contentHeight + 8.dp + overflow * 2).testTag("navigation-optical-container")) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(contentHeight + 8.dp)
                .testTag("navigation-panel").then(if (backgroundVisible) Modifier.liquidPanel(backdrop, blur) else Modifier))
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
                    val base = Modifier.align(AbsoluteAlignment.CenterLeft)
                        .width(with(density) { slot.toDp() }).height(contentHeight)
                    val optical = if (blur) base.drawBackdrop(backdrop,
                        shape = { indicatorShape },
                        effects = {
                            if (!bubbleFailed.get()) try {
                                colorControls(saturation = 1.38f)
                                blur(3.dp.toPx())
                                if (tier == GlassTier.REFRACTIVE) lens(12.dp.toPx(), 22.dp.toPx(),
                                    chromaticAberration = true)
                            } catch (_: Exception) {
                                renderEffect = null; padding = 0f
                                bubbleFailed.set(true); bubbleFailure()
                            }
                        },
                        layerBlock = {
                            val p = navigation.position.coerceIn(0f, pages.lastIndex.toFloat())
                            translationX = (if (rtl) pages.lastIndex - p else p) * slot
                            this.scaleX = scaleXState.value * (1f + (abs(speedState.value) * .006f).coerceIn(0f, .014f))
                            this.scaleY = scaleYState.value * (1f - (abs(speedState.value) * .008f).coerceIn(0f, .035f))
                        },
                        highlight = { Highlight(width = .9.dp, alpha = .8f) },
                        shadow = { Shadow(radius = 9.dp, alpha = .17f) },
                        innerShadow = { InnerShadow(radius = 3.dp, alpha = .12f) },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = if (bubbleFailed.get()) .62f
                                else if (dark) .23f else .42f))
                        }) else base.graphicsLayer {
                            val p = navigation.position.coerceIn(0f, pages.lastIndex.toFloat())
                            translationX = (if (rtl) pages.lastIndex - p else p) * slot
                            this.scaleX = scaleXState.value
                            this.scaleY = scaleYState.value
                        }.background(Brush.verticalGradient(listOf(
                            Color.White.copy(alpha = if (dark) .18f else .50f),
                            accent.copy(alpha = if (miuix) .20f else .12f))), indicatorShape)
                            .border(.8.dp, Color.White.copy(alpha = .45f), indicatorShape)
                    Box(optical.testTag("navigation-indicator"))
                  }
                }
                bubble()
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    pages.forEachIndexed { index, name ->
                        NavigationTab(index, name, index == selection, sources[index], navigation, Modifier.weight(1f)) {
                            navigation.select(index); latestOnSelect(index)
                        }
                    }
                }
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
        GalaxyText(name, color = tint, style = GalaxyTypography.labelSmall,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Modifier.liquidPanel(backdrop: Backdrop, enabled: Boolean, shadow: Boolean = true): Modifier {
    val shape = RoundedCornerShape(50)
    val tier = LocalGlassTier.current
    if (!enabled) return if (tier == GlassTier.OFF) background(Panel, shape)
        else galaxyGlass(shape, enabled = false)
    val tokens = LocalAppDesignTokens.current
    val fallback = Panel
    val failure = LocalGlass.current.onFailure
    val failed = remember(backdrop) { AtomicBoolean(false) }
    return drawBackdrop(backdrop, shape = { shape }, effects = {
        if (!failed.get()) try {
            colorControls(saturation = 1.08f)
            blur(tokens.glassBlur.toPx())
            if (tier == GlassTier.REFRACTIVE) lens(tokens.glassLensHeight.toPx(), tokens.glassLensAmount.toPx())
        }
        catch (_: Exception) { renderEffect = null; padding = 0f; failed.set(true); failure() }
    }, highlight = { Highlight(width = tokens.glassHighlightWidth, alpha = .62f) },
        shadow = if (shadow) ({ Shadow(radius = tokens.glassShadowRadius, alpha = .15f) }) else null,
        innerShadow = { InnerShadow(radius = 4.dp, alpha = .09f) },
        onDrawSurface = {
            if (failed.get()) drawRect(fallback)
            else drawRect(Brush.verticalGradient(listOf(
                fallback.copy(alpha = .30f), fallback.copy(alpha = .56f))))
        })
}

