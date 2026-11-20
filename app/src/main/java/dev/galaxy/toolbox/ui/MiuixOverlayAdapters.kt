package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * A controlled confirmation dialog. The owner changes [show] to false in either callback.
 * Keep this composable mounted while [show] becomes false so MIUIX can finish its exit animation.
 * MIUIX already handles predictive Back, outside taps, insets, dimming and squircle corners.
 */
@Composable
internal fun MiuixConfirmationDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmLabel: String = "確認",
    cancelLabel: String = "取消",
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    hostedByMiuixScaffold: Boolean = false,
) {
    val language = LocalAppLanguage.current
    MiuixContentDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        hostedByMiuixScaffold = hostedByMiuixScaffold,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = AppText.translate(cancelLabel, language), onClick = onDismissRequest, modifier = Modifier.weight(1f))
            TextButton(
                text = AppText.translate(confirmLabel, language),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

/**
 * A MIUIX dialog for custom content. Window mode works without a MIUIX Scaffold popup host;
 * overlay mode should only be selected after the parent is wrapped in MIUIX Scaffold.
 */
@Composable
internal fun MiuixContentDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    hostedByMiuixScaffold: Boolean = false,
    content: @Composable () -> Unit,
) {
    val language = LocalAppLanguage.current
    val displayedTitle = title?.let { AppText.translate(it, language) }
    val displayedSummary = summary?.let { AppText.translate(it, language) }
    if (hostedByMiuixScaffold) {
        OverlayDialog(
            show = show,
            modifier = modifier,
            title = displayedTitle,
            summary = displayedSummary,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            content = content,
        )
    } else {
        WindowDialog(
            show = show,
            modifier = modifier,
            title = displayedTitle,
            summary = displayedSummary,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            content = content,
        )
    }
}

/**
 * A MIUIX bottom sheet for scrollable details. The caller owns content scrolling and the
 * [show] state; Back, drag and outside-tap dismiss are handled by the MIUIX component.
 * Keep this composable mounted during the false state to allow the hide animation to complete.
 */
@Composable
internal fun MiuixContentSheet(
    show: Boolean,
    title: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    allowDismiss: Boolean = true,
    hostedByMiuixScaffold: Boolean = false,
    content: @Composable () -> Unit,
) {
    val language = LocalAppLanguage.current
    val displayedTitle = title?.let { AppText.translate(it, language) }
    if (hostedByMiuixScaffold) {
        OverlayBottomSheet(
            show = show,
            modifier = modifier,
            title = displayedTitle,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            content = content,
        )
    } else {
        WindowBottomSheet(
            show = show,
            modifier = modifier,
            title = displayedTitle,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            allowDismiss = allowDismiss,
            content = content,
        )
    }
}
