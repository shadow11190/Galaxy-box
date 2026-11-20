package dev.galaxy.toolbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Keeps MIUIX's close animation mounted until the parent dismisses the detail page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalaxyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    containerColor: Color = Panel,
    hostedByMiuixScaffold: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppStyle.current == AppStyle.MIUIX) {
        var shown by remember { mutableStateOf(true) }
        MiuixContentSheet(show = shown, modifier = modifier,
            onDismissRequest = { shown = false }, onDismissFinished = onDismissRequest,
            hostedByMiuixScaffold = hostedByMiuixScaffold) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    } else {
        if (sheetState == null) {
            ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier,
                containerColor = containerColor, content = content)
        } else {
            ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier,
                sheetState = sheetState, containerColor = containerColor, content = content)
        }
    }
}
