package dev.galaxy.toolbox.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import dev.galaxy.toolbox.model.BatteryPresentation
import dev.galaxy.toolbox.model.Reading

internal fun LazyListScope.batteryInformation(readings: List<Reading>) {
    items(BatteryPresentation.visible(readings)) { ReadingCard(it) }
}
