package dev.galaxy.toolbox.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import dev.galaxy.toolbox.MainActivity
import dev.galaxy.toolbox.R
import dev.galaxy.toolbox.ui.AppText
import dev.galaxy.toolbox.ui.AppDisplayPreferences

object WidgetRenderer {
    fun create(context: Context, kind: WidgetKind, id: Int, data: WidgetPresentation,
        width: Int = 280, height: Int = 280, settings: WidgetSettings = WidgetSettings.defaults(kind), preview: Boolean = false): RemoteViews {
        if (kind == WidgetKind.STRIP) return createAccessoryBar(context, id, data, width, height, settings, preview)
        val language = AppDisplayPreferences.language(context)
        fun localize(value: String) = AppText.translate(value, language)
        val strip = kind == WidgetKind.STRIP
        val tile = strip && width < 140
        val views = RemoteViews(context.packageName, when { tile -> R.layout.widget_dashboard_tile; strip -> R.layout.widget_dashboard_strip; else -> R.layout.widget_dashboard })
        val plan = WidgetLayoutPlan.forSize(width, height, context.resources.configuration.fontScale, settings)
        // Reapply-safe: reset the whole background and image, including a previous decorated mode.
        views.setInt(R.id.widget_root, "setBackgroundResource", 0)
        views.setImageViewResource(R.id.widget_background_layer, 0)
        val background = WidgetBackgroundPainter.create(settings, width, height)
        views.setViewVisibility(R.id.widget_background_layer, if (background == null) View.GONE else View.VISIBLE)
        if (background != null) views.setImageViewBitmap(R.id.widget_background_layer, background)
        views.setTextColor(R.id.widget_brand, settings.accentColor)
        views.setTextColor(R.id.widget_refresh, settings.accentColor)
        views.setContentDescription(R.id.widget_refresh, localize("重新取樣小工具資料"))
        views.setTextColor(R.id.widget_clock, settings.textColor)
        views.setTextColor(R.id.widget_model, settings.textColor)
        views.setTextColor(R.id.widget_updated, settings.textColor)
        views.setTextViewText(R.id.widget_model, data.model)
        views.setTextViewText(R.id.widget_updated, localize(data.updated))
        views.setViewVisibility(R.id.widget_clock, if (settings.showClock && !tile) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_model_row, if (settings.showModel && !tile) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_updated, if (tile) View.GONE else View.VISIBLE)
        val logo = if (settings.showModel && !tile) WidgetBrandBitmap.create(context, data.brandMark, settings.textColor) else null
        views.setViewVisibility(R.id.widget_model_logo, if (logo == null) View.GONE else View.VISIBLE)
        if (logo != null) views.setImageViewBitmap(R.id.widget_model_logo, logo)
        views.removeAllViews(R.id.widget_metrics)
        val selected = WidgetField.entries.filter { it in settings.fields }
        val visible = if (strip) selected.take(when { width < 210 -> 1; width < 290 -> 2; width < 370 -> 3; else -> 4 }) else selected
        val groups = if (strip) listOf(visible) else visible.chunked(plan.columns)
        groups.forEach { fields ->
            val row = if (strip) null else RemoteViews(context.packageName, R.layout.widget_metric_row)
            fields.forEach { field ->
                val metric = data.metric(field)
                val label = localize(metric.label)
                val value = localize(metric.value)
                val detail = localize(metric.detail)
                val cell = RemoteViews(context.packageName, when { strip -> R.layout.widget_metric_strip; plan.dense -> R.layout.widget_metric_dense; else -> R.layout.widget_metric_cell })
                cell.setTextViewText(R.id.widget_metric_label, label)
                cell.setTextViewText(R.id.widget_metric_value, value)
                cell.setTextViewText(R.id.widget_metric_detail, detail)
                cell.setTextColor(R.id.widget_metric_label, settings.textColor)
                cell.setTextColor(R.id.widget_metric_value, settings.accentColor)
                cell.setTextColor(R.id.widget_metric_detail, settings.textColor)
                cell.setContentDescription(R.id.widget_cell, "$label $value $detail")
                cell.setViewVisibility(R.id.widget_metric_detail, if (!strip && plan.details) View.VISIBLE else View.GONE)
                cell.setViewVisibility(R.id.widget_metric_progress, if (metric.progress == null) View.GONE else View.VISIBLE)
                metric.progress?.let { cell.setImageViewBitmap(R.id.widget_metric_progress, WidgetBackgroundPainter.progress(it, settings.accentColor)) }
                if (strip) views.addView(R.id.widget_metrics, cell) else row!!.addView(R.id.widget_row, cell)
            }
            if (row != null) views.addView(R.id.widget_metrics, row)
        }
        val clickIntent = when (settings.click) {
            WidgetClick.APP -> Intent(context, MainActivity::class.java).setData(Uri.parse("galaxybox://widget/open/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            WidgetClick.SETTINGS -> configurationIntent(context, id)
            WidgetClick.NONE -> null
        }
        views.setOnClickPendingIntent(R.id.widget_root, if (preview || clickIntent == null) null else PendingIntent.getActivity(context, id, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_refresh, if (preview) null else PendingIntent.getBroadcast(context, id,
            Intent(context, WidgetUpdates.providerClass(kind)).setAction(WidgetUpdates.REFRESH)
                .setData(Uri.parse("galaxybox://widget/refresh/${kind.name}/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }
    private fun createAccessoryBar(context: Context, id: Int, data: WidgetPresentation, width: Int, height: Int,
        settings: WidgetSettings, preview: Boolean): RemoteViews {
        val language = AppDisplayPreferences.language(context)
        fun localize(value: String) = AppText.translate(value, language)
        val views = RemoteViews(context.packageName, R.layout.widget_accessories)
        views.setInt(R.id.widget_root, "setBackgroundResource", 0)
        views.setImageViewResource(R.id.widget_background_layer, 0)
        val background = WidgetBackgroundPainter.create(settings, width, 68, pill = true)
        views.setViewVisibility(R.id.widget_background_layer, if (background == null) View.GONE else View.VISIBLE)
        if (background != null) views.setImageViewBitmap(R.id.widget_background_layer, background)
        views.setTextColor(R.id.widget_refresh, settings.accentColor)
        views.setContentDescription(R.id.widget_refresh, localize("重新取樣小工具資料"))
        views.removeAllViews(R.id.widget_battery_slots)
        val slots = when { width < 175 -> 2; width < 245 -> 3; width < 315 -> 4; else -> 5 }
        repeat(slots) { index ->
            val battery = data.connectedBatteries.getOrNull(index)
            val cell = RemoteViews(context.packageName, R.layout.widget_battery_slot)
            cell.setImageViewBitmap(R.id.widget_battery_ring, WidgetBatteryArc.draw(battery?.percent,
                battery?.kind ?: ConnectedBattery.Kind.ACCESSORY, settings.accentColor, settings.textColor, showGlyph = battery != null))
            cell.setTextViewText(R.id.widget_battery_value, battery?.percent?.let { "$it%" } ?: if (battery == null) "" else "—")
            val batteryName = battery?.name.orEmpty().let { if (it == "手機" || it == "藍牙裝置") localize(it) else it }
            cell.setTextViewText(R.id.widget_battery_name, batteryName)
            cell.setTextColor(R.id.widget_battery_value, settings.textColor)
            cell.setTextColor(R.id.widget_battery_name, settings.textColor)
            cell.setViewVisibility(R.id.widget_battery_value, if (battery == null) View.GONE else View.VISIBLE)
            cell.setViewVisibility(R.id.widget_battery_name, if (battery == null || width < 175 || context.resources.configuration.fontScale > 1.5f) View.GONE else View.VISIBLE)
            cell.setContentDescription(R.id.widget_battery_slot, if (battery == null) localize(data.accessoryStatus ?: "尚無已連線裝置")
                else "$batteryName ${battery.percent?.let { "$it%" } ?: localize("未知")}")
            views.addView(R.id.widget_battery_slots, cell)
        }
        val clickIntent = when (settings.click) {
            WidgetClick.APP -> Intent(context, MainActivity::class.java).setData(Uri.parse("galaxybox://widget/open/$id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            WidgetClick.SETTINGS -> configurationIntent(context, id)
            WidgetClick.NONE -> null
        }
        views.setOnClickPendingIntent(R.id.widget_root, if (preview || clickIntent == null) null else PendingIntent.getActivity(context, id, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_refresh, if (preview) null else PendingIntent.getBroadcast(context, id,
            Intent(context, StripWidgetProvider::class.java).setAction(WidgetUpdates.REFRESH)
                .setData(Uri.parse("galaxybox://widget/refresh/STRIP/$id")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }
    fun configurationIntent(context: Context, id: Int) = Intent(context, WidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(WidgetConfigurationActivity.EXTRA_RETURN_TO_OVERVIEW, true)
        .setData(Uri.parse("galaxybox://widget/settings/$id"))
}
