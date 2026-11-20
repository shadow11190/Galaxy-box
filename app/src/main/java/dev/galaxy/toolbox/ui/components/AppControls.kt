package dev.galaxy.toolbox.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable internal fun AppButton(text: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    GalaxyPrimaryTextButton(text, onClick, modifier, enabled)
}

@Composable internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    GalaxySwitch(checked, onCheckedChange, modifier, enabled)
}

/** A compact information or settings row without a second card around it. */
@Composable internal fun AppListItem(title: String, modifier: Modifier = Modifier,
    supporting: String? = null, onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null) {
    val ink = GalaxyPalette.onSurface
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GalaxyText(title, color = ink, style = GalaxyTypography.bodyLarge)
            supporting?.let { GalaxyText(it, color = Muted, style = GalaxyTypography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable internal fun AppSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        SwitchPreference(checked = checked, onCheckedChange = onCheckedChange,
            title = AppText.translate(title, LocalAppLanguage.current), insideMargin = PaddingValues(0.dp))
    } else {
        AppListItem(title, trailing = { AppSwitch(checked, onCheckedChange) })
    }
}

/** Same-root Backdrop dialog in Liquid Glass; MIUIX owns its native window dialog. */
@Composable internal fun AppDialog(show: Boolean, title: String, onDismiss: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixContentDialog(show = show, title = AppText.translate(title, LocalAppLanguage.current),
            onDismissRequest = onDismiss) {
            Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        return
    }
    BackHandler(enabled = show, onBack = onDismiss)
    AnimatedVisibility(show,
        enter = fadeIn() + scaleIn(initialScale = .92f, transformOrigin = TransformOrigin(1f, 0f),
            animationSpec = spring(dampingRatio = .84f, stiffness = 440f)),
        exit = fadeOut() + scaleOut(targetScale = .96f, transformOrigin = TransformOrigin(1f, 0f))) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .48f))
                .clickable(onClick = onDismiss))
            val shape = GalaxySquircleShape(LocalAppDesignTokens.current.floatingRadius)
            Surface(modifier.align(Alignment.Center).fillMaxWidth().widthIn(max = 420.dp)
                .padding(horizontal = 18.dp).galaxyGlass(shape, floating = true)
                .clickable(interactionSource = remember { MutableInteractionSource() },
                    indication = null, onClick = {}),
                shape = shape,
                color = if (LocalGlassTier.current == GlassTier.OFF) Panel else Color.Transparent,
                contentColor = GalaxyPalette.onSurface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GalaxyText(title, style = GalaxyTypography.titleLarge, fontWeight = FontWeight.SemiBold)
                    content()
                }
            }
        }
    }
}
