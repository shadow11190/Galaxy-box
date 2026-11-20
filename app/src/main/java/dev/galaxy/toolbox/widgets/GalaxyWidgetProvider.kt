package dev.galaxy.toolbox.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.net.TrafficStats
import dev.galaxy.toolbox.data.DeviceNames
import dev.galaxy.toolbox.data.BrandMarks
import dev.galaxy.toolbox.data.HardwareCatalog
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import dev.galaxy.toolbox.MainActivity
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.data.HardwareRepository
import dev.galaxy.toolbox.model.Snapshot
import java.util.concurrent.Executors

class OverviewWidgetProvider : GalaxyWidgetProvider() { override val kind = WidgetKind.OVERVIEW }
class BatteryWidgetProvider : GalaxyWidgetProvider() { override val kind = WidgetKind.BATTERY }
class ResourcesWidgetProvider : GalaxyWidgetProvider() { override val kind = WidgetKind.RESOURCES }
class WideWidgetProvider : GalaxyWidgetProvider() { override val kind = WidgetKind.WIDE }
class StripWidgetProvider : GalaxyWidgetProvider() { override val kind = WidgetKind.STRIP }

abstract class GalaxyWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = refresh(context)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdates.REFRESH) refresh(context) else super.onReceive(context, intent)
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetSettingsStore(context)
        appWidgetIds.forEach(store::delete)
    }
    private fun refresh(context: Context) {
        val pending = goAsync()
        worker.execute {
            try { runCatching { WidgetUpdates.refresh(context.applicationContext, kind) } }
            finally { pending?.finish() }
        }
    }
    companion object {
        private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "GalaxyBoxWidget").apply { isDaemon = true } }
    }
}

object WidgetUpdates {
    const val REFRESH = "dev.galaxy.toolbox.widget.REFRESH"
    fun providerClass(kind: WidgetKind): Class<out GalaxyWidgetProvider> = when (kind) {
        WidgetKind.OVERVIEW -> OverviewWidgetProvider::class.java
        WidgetKind.BATTERY -> BatteryWidgetProvider::class.java
        WidgetKind.RESOURCES -> ResourcesWidgetProvider::class.java
        WidgetKind.WIDE -> WideWidgetProvider::class.java
        WidgetKind.STRIP -> StripWidgetProvider::class.java
    }
    fun component(context: Context, kind: WidgetKind) = ComponentName(context, providerClass(kind))
    fun refresh(context: Context, kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(component(context, kind))
        if (ids.isEmpty()) return
        val snapshot = runCatching { HardwareRepository(context).sample() }.getOrElse { Snapshot() }
        publish(context, manager, kind, ids, snapshot, forceAccessories = true)
    }
    fun publishAll(context: Context, snapshot: Snapshot) {
        val manager = AppWidgetManager.getInstance(context)
        WidgetKind.entries.forEach { kind ->
            val ids = manager.getAppWidgetIds(component(context, kind))
            if (ids.isNotEmpty()) publish(context, manager, kind, ids, snapshot)
        }
    }
    fun kindFor(context: Context, id: Int): WidgetKind? {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return null
        return WidgetKind.entries.firstOrNull { component(context, it) == info.provider }
    }
    fun installed(context: Context): List<Pair<Int, WidgetKind>> {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { kind -> manager.getAppWidgetIds(component(context, kind)).map { it to kind } }
    }
    private fun publish(context: Context, manager: AppWidgetManager, kind: WidgetKind, ids: IntArray, snapshot: Snapshot,
        forceAccessories: Boolean = false) {
        val data = WidgetDataReader.presentation(context, snapshot, includeAccessories = kind == WidgetKind.STRIP,
            forceAccessories = forceAccessories)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val settings = WidgetSettingsStore(context).load(id, kind)
            fun size(key: String, fallback: Int) = options.getInt(key, fallback).takeIf { it > 0 } ?: fallback
            val width = size(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            val height = size(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, if (kind == WidgetKind.OVERVIEW || kind == WidgetKind.WIDE) 280 else 96)
            val sizes = if (Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                    ?.filter { it.width > 0 && it.height > 0 }?.distinct()?.take(4)
            } else null
            val views = if (Build.VERSION.SDK_INT >= 31 && !sizes.isNullOrEmpty()) {
                RemoteViews(sizes.associateWith { WidgetRenderer.create(context, kind, id, data, it.width.toInt(), it.height.toInt(), settings) })
            } else {
                val portrait = WidgetRenderer.create(context, kind, id, data, width, height, settings)
                val landscape = WidgetRenderer.create(context, kind, id, data,
                    size(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width), size(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height), settings)
                RemoteViews(landscape, portrait)
            }
            manager.updateAppWidget(id, views)
        }
    }
}

object WidgetDataReader {
    private data class DeviceLabel(val model: String, val mark: String?)
    @Volatile private var resolvedDevice: DeviceLabel? = null
    private fun device(context: Context): DeviceLabel = resolvedDevice ?: run {
        val verified = runCatching { HardwareCatalog.identity(context, reports = emptyList()).name }.getOrNull()
            ?.takeUnless { it.isBlank() || it == "—" || DeviceNames.looksLikeModelCode(it, Build.MODEL) }
        val label = DeviceLabel(verified ?: Build.MODEL.takeUnless { it.isBlank() || it == Build.UNKNOWN } ?: "—",
            verified?.let { BrandMarks.resolve(Build.MANUFACTURER, Build.BRAND, it) })
        resolvedDevice = label
        label
    }
    fun presentation(context: Context, snapshot: Snapshot, includeAccessories: Boolean = false,
        forceAccessories: Boolean = false): WidgetPresentation {
        val traffic = runCatching {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx < 0 || tx < 0) null else Math.addExact(rx, tx)
        }.getOrNull()
        val label = if (includeAccessories) null else device(context)
        val base = WidgetPresentation.from(snapshot, model = label?.model ?: "—", trafficBytes = traffic,
            brandMark = label?.mark, uptimeMillis = android.os.SystemClock.elapsedRealtime())
        if (!includeAccessories) return base
        val remote = ConnectedBatteryReader.read(context, forceAccessories)
        return base.copy(connectedBatteries = listOf(ConnectedBattery("手機", snapshot.batteryPercent?.takeIf { it in 0..100 }, ConnectedBattery.Kind.PHONE)) + remote.devices,
            accessoryStatus = remote.status)
    }
    fun read(context: Context, includeAccessories: Boolean = false) = presentation(context,
        runCatching { HardwareRepository(context).sample() }.getOrElse { Snapshot() }, includeAccessories,
        forceAccessories = includeAccessories)
}
