package dev.galaxy.toolbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Allows local JVM previews to use the path silhouette when native AGSL is unavailable. */
internal val LocalNativeMiuixCard = staticCompositionLocalOf { true }

/** A page's section label, aligned with the grouped card below it. */
@Composable
internal fun MiuixSectionHeader(title: String, modifier: Modifier = Modifier) {
    SmallTitle(
        text = AppText.translate(title, LocalAppLanguage.current),
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 6.dp),
    )
}

/** Indented separator for adjacent rows inside one grouped card. */
@Composable
internal fun MiuixGroupDivider(
    modifier: Modifier = Modifier,
    startInset: Dp = 16.dp,
    endInset: Dp = 16.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset, end = endInset),
        thickness = Dp.Hairline,
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/** MIUIX geometry with the same optional background capture as the other style. */
@Composable
internal fun Modifier.miuixGroupedSurface(
    cornerRadius: Dp = 20.dp,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
): Modifier {
    val shape = remember(cornerRadius) { GalaxySquircleShape(cornerRadius) }
    return if (LocalGlassTier.current == GlassTier.OFF) this.clip(shape).background(color)
        else this.galaxyGlass(shape, backdrop = LocalCardBackdrop.current, enabled = false).clip(shape)
}

/** One MIUIX group; callers place related rows and [MiuixGroupDivider] inside it. */
@Composable
internal fun MiuixGroupedCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    insidePadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalSpacing: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalNativeMiuixCard.current || LocalGlassTier.current != GlassTier.OFF) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = modifier.fillMaxWidth()
                .miuixGroupedSurface(cornerRadius)
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ) else Modifier,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(insidePadding),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = cornerRadius,
            insideMargin = insidePadding,
            colors = CardDefaults.defaultColors(),
            pressFeedbackType = if (onClick == null) PressFeedbackType.None else PressFeedbackType.Sink,
            showIndication = false,
            onClick = onClick,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = content,
            )
        }
    }
}

/** A labelled group that can replace the current MIUIX branch of InfoCard. */
@Composable
internal fun MiuixGroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MiuixSectionHeader(title)
        MiuixGroupedCard(content = content)
    }
}
