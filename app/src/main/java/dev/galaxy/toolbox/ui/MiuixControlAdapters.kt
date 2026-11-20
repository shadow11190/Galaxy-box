package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.FilterChip as MaterialFilterChip
import androidx.compose.material3.LocalContentColor as MaterialContentColor
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.OutlinedTextField as MaterialOutlinedTextField
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Controls shared by the original and MIUIX styles. Glass remains an independent setting. */
@Composable
internal fun GalaxyPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColorsPrimary(),
        ) {
            // Existing callers can still supply Material Text during migration.
            CompositionLocalProvider(MaterialContentColor provides MiuixTheme.colorScheme.onPrimary) {
                content()
            }
        }
    } else {
        MaterialButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, content = content)
    }
}

/** Preferred when a button has a plain text label; typography is native to each style. */
@Composable
internal fun GalaxyPrimaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = AppText.translate(text, LocalAppLanguage.current)
    GalaxyPrimaryButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        if (LocalAppStyle.current == AppStyle.MIUIX) MiuixText(label) else MaterialText(label)
    }
}

/** MIUIX TextButton has a text-only API and renders a muted filled button. */
@Composable
internal fun GalaxyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = AppText.translate(text, LocalAppLanguage.current)
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixTextButton(text = label, onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled)
    } else {
        MaterialTextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled) {
            MaterialText(label)
        }
    }
}

/** Content-slot fallback for links/actions that also contain an icon. */
@Composable
internal fun GalaxyTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            insideMargin = contentPadding,
            colors = MiuixButtonDefaults.buttonColors(),
        ) {
            CompositionLocalProvider(MaterialContentColor provides MiuixTheme.colorScheme.onSecondaryVariant) {
                content()
            }
        }
    } else {
        MaterialTextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
            contentPadding = contentPadding, content = content)
    }
}

@Composable
internal fun GalaxyFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
            enabled = enabled,
            minWidth = 0.dp,
            minHeight = 48.dp,
            cornerRadius = 17.dp,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            colors = if (selected) MiuixButtonDefaults.buttonColorsPrimary() else MiuixButtonDefaults.buttonColors(),
        ) {
            leadingIcon?.let { icon -> icon(); Spacer(Modifier.width(6.dp)) }
            label()
        }
    } else {
        MaterialFilterChip(selected = selected, onClick = onClick, label = label,
            modifier = modifier.heightIn(min = 48.dp), enabled = enabled, leadingIcon = leadingIcon)
    }
}

/** An outlined action in the original style becomes a MIUIX secondary filled action. */
@Composable
internal fun GalaxyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
            colors = MiuixButtonDefaults.buttonColors(),
        ) {
            CompositionLocalProvider(MaterialContentColor provides MiuixTheme.colorScheme.onSecondaryVariant) {
                content()
            }
        }
    } else {
        MaterialOutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, content = content)
    }
}

@Composable
internal fun GalaxyOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = AppText.translate(text, LocalAppLanguage.current)
    GalaxyOutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        if (LocalAppStyle.current == AppStyle.MIUIX) MiuixText(label) else MaterialText(label)
    }
}

/** MIUIX has no FilterChip; its compact selected/secondary buttons represent one choice. */
@Composable
internal fun GalaxyChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val displayedLabel = AppText.translate(label, LocalAppLanguage.current)
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
            enabled = enabled,
            minWidth = 0.dp,
            minHeight = 48.dp,
            cornerRadius = 17.dp,
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            colors = if (selected) MiuixButtonDefaults.buttonColorsPrimary()
                else MiuixButtonDefaults.buttonColors(),
        ) {
            MiuixText(displayedLabel, style = MiuixTheme.textStyles.button)
        }
    } else {
        MaterialFilterChip(
            selected = selected,
            onClick = onClick,
            label = { MaterialText(displayedLabel) },
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled,
        )
    }
}

@Composable
internal fun GalaxySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    } else {
        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

@Composable
internal fun GalaxySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        MaterialSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

/** MIUIX uses its filled squircle input; the original style keeps its outlined field. */
@Composable
internal fun GalaxyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val displayedLabel = AppText.translate(label, LocalAppLanguage.current)
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            label = displayedLabel,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
        )
    } else {
        MaterialOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { MaterialText(displayedLabel) },
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            keyboardOptions = keyboardOptions,
        )
    }
}
