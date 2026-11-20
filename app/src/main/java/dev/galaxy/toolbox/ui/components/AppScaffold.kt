package dev.galaxy.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

internal data class AppBackdropSources(val background: LayerBackdrop, val content: LayerBackdrop, val controls: Backdrop)

@Composable internal fun rememberAppBackdrops(): AppBackdropSources {
    val background = rememberLayerBackdrop()
    val content = rememberLayerBackdrop()
    val controls = rememberCombinedBackdrop(background, content)
    return remember(background, content, controls) { AppBackdropSources(background, content, controls) }
}

internal fun Modifier.captureAppBackdrop(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

internal val LocalAppNestedScrollConnection = staticCompositionLocalOf<NestedScrollConnection?> { null }

/** One page host for both visual systems; page state and navigation motion stay with the caller. */
@Composable internal fun AppScaffold(
    pages: List<String>, selectedPage: Int, onSelect: (Int) -> Unit,
    backdrop: Backdrop, motion: NavigationMotion, navigationBackground: Boolean,
    scrollState: LazyListState, onSettings: () -> Unit, onAbout: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideNavigation = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wideNavigation) AdaptiveNavigationRail(pages, selectedPage, onSelect)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (LocalAppStyle.current == AppStyle.MIUIX) {
                    val behavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()
                    CompositionLocalProvider(LocalAppNestedScrollConnection provides behavior.nestedScrollConnection) {
                        MiuixScaffold(containerColor = Color.Transparent,
                            topBar = {
                                MiuixTopAppBar(title = pages[selectedPage], largeTitle = pages[selectedPage],
                                    color = Color.Transparent, scrollBehavior = behavior, actions = {
                                        HeaderActions(onSettings, onAbout, Modifier.padding(end = 4.dp))
                                    })
                            },
                            bottomBar = { if (!wideNavigation) AppBottomBar(pages, selectedPage, onSelect,
                                backdrop, motion, navigationBackground) },
                            content = content)
                    }
                } else {
                    var navigationHeightPx by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current
                    Box(Modifier.fillMaxSize()) {
                        content(PaddingValues(
                            top = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding() + 54.dp,
                            bottom = if (wideNavigation) WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                                .asPaddingValues().calculateBottomPadding()
                                else with(density) { navigationHeightPx.toDp() },
                        ))
                        AppTopBar(pages[selectedPage], scrollState, onSettings, onAbout,
                            modifier = Modifier.align(Alignment.TopCenter))
                        if (!wideNavigation) Box(Modifier.align(Alignment.BottomCenter)
                            .onSizeChanged { navigationHeightPx = it.height }) {
                            AppBottomBar(pages, selectedPage, onSelect, backdrop, motion, navigationBackground)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun AppTopBar(
    title: String, scrollState: LazyListState, onSettings: () -> Unit, onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().statusBarsPadding().height(54.dp)) {
        GalaxyText(title, modifier = Modifier.align(Alignment.Center).graphicsLayer {
            val offset = if (scrollState.firstVisibleItemIndex > 0) 1f
                else (scrollState.firstVisibleItemScrollOffset / 90f).coerceIn(0f, 1f)
            alpha = ((offset - .40f) / .60f).coerceIn(0f, 1f)
        }, style = GalaxyTypography.titleMedium, fontWeight = FontWeight.SemiBold)
        HeaderActions(onSettings, onAbout, Modifier.align(Alignment.CenterEnd)
            .padding(end = LocalAppDesignTokens.current.sideInset))
    }
}

@Composable private fun HeaderActions(
    onSettings: () -> Unit, onAbout: () -> Unit, modifier: Modifier = Modifier,
) {
    val miuix = LocalAppStyle.current == AppStyle.MIUIX
    val shape = remember(miuix) {
        if (miuix) GalaxySquircleShape(24.dp) else RoundedCornerShape(28.dp)
    }
    val surface = if (LocalGlassTier.current == GlassTier.OFF) {
        if (miuix) Modifier else Modifier.background(Panel, shape)
    } else Modifier.galaxyGlass(shape, compact = true)
    Row(modifier.then(surface).clip(shape)) {
        HeaderIconButton(HeaderIcon.SETTINGS, "設定", grouped = true, onClick = onSettings)
        HeaderIconButton(HeaderIcon.ABOUT, "關於本應用", grouped = true, onClick = onAbout)
    }
}

@Composable internal fun AppLargeTitle(title: String, scrollState: LazyListState) {
    GalaxyText(title, modifier = Modifier.fillMaxWidth().graphicsLayer {
        val offset = if (scrollState.firstVisibleItemIndex > 0) 1f
            else (scrollState.firstVisibleItemScrollOffset / 90f).coerceIn(0f, 1f)
        alpha = 1f - offset
        scaleX = 1f - .07f * offset
        scaleY = 1f - .07f * offset
    }, style = GalaxyTypography.headlineLarge, fontWeight = FontWeight.Bold)
}

@Composable internal fun AppBottomBar(pages: List<String>, selected: Int, onSelect: (Int) -> Unit,
    backdrop: Backdrop, motion: NavigationMotion, backgroundVisible: Boolean) {
    val tier = LocalGlassTier.current
    GlassNavigation(pages, selected, onSelect, backdrop,
        tier == GlassTier.REFRACTIVE || tier == GlassTier.BLUR,
        motion, backgroundVisible)
}
