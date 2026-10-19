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

object WidgetRenderer {
    fun create(context: Context, kind: WidgetKind, id: Int, data: WidgetPresentation,
        width: Int = 280, height: Int = 280, settings: WidgetSettings = WidgetSettings.defaults(kind), preview: Boolean = false): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dashboard)
        val plan = WidgetLayoutPlan.forSize(width, height, context.resources.configuration.fontScale, settings)
        // Reapply-safe: reset the whole background and image, including a previous decorated mode.
        views.setInt(R.id.widget_root, "setBackgroundResource", 0)
        views.setImageViewResource(R.id.widget_background_layer, 0)
        val background = WidgetBackgroundPainter.create(settings, width, height)
        views.setViewVisibility(R.id.widget_background_layer, if (background == null) View.GONE else View.VISIBLE)
        if (background != null) views.setImageViewBitmap(R.id.widget_background_layer, background)
        views.setTextColor(R.id.widget_brand, settings.accentColor)
        views.setTextColor(R.id.widget_refresh, settings.accentColor)
        views.setTextColor(R.id.widget_clock, settings.textColor)
        views.setTextColor(R.id.widget_model, settings.textColor)
        views.setTextColor(R.id.widget_updated, settings.textColor)
        views.setTextViewText(R.id.widget_model, data.model)
        views.setTextViewText(R.id.widget_updated, data.updated)
        views.setViewVisibility(R.id.widget_clock, if (settings.showClock) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_model, if (settings.showModel) View.VISIBLE else View.GONE)
        views.removeAllViews(R.id.widget_metrics)
        WidgetField.entries.filter { it in settings.fields }.chunked(plan.columns).forEach { fields ->
            val row = RemoteViews(context.packageName, R.layout.widget_metric_row)
            fields.forEach { field ->
                val metric = data.metric(field)
                val cell = RemoteViews(context.packageName, if (plan.dense) R.layout.widget_metric_dense else R.layout.widget_metric_cell)
                cell.setTextViewText(R.id.widget_metric_label, metric.label)
                cell.setTextViewText(R.id.widget_metric_value, metric.value)
                cell.setTextViewText(R.id.widget_metric_detail, metric.detail)
                cell.setTextColor(R.id.widget_metric_label, settings.textColor)
                cell.setTextColor(R.id.widget_metric_value, settings.accentColor)
                cell.setTextColor(R.id.widget_metric_detail, settings.textColor)
                cell.setContentDescription(R.id.widget_cell, "${metric.label} ${metric.value} ${metric.detail}")
                cell.setViewVisibility(R.id.widget_metric_detail, if (plan.details) View.VISIBLE else View.GONE)
                cell.setViewVisibility(R.id.widget_metric_progress, if (metric.progress == null) View.GONE else View.VISIBLE)
                metric.progress?.let { cell.setImageViewBitmap(R.id.widget_metric_progress, WidgetBackgroundPainter.progress(it, settings.accentColor)) }
                row.addView(R.id.widget_row, cell)
            }
            views.addView(R.id.widget_metrics, row)
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
    fun configurationIntent(context: Context, id: Int) = Intent(context, WidgetConfigurationActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).setData(Uri.parse("galaxybox://widget/settings/$id"))
}
