package dev.galaxy.toolbox.ui

import android.content.Intent
import android.Manifest
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.galaxy.toolbox.monitor.FloatingMonitorService
import dev.galaxy.toolbox.monitor.PerformanceNotificationService

@Composable internal fun FloatingMonitorCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val running by FloatingMonitorService.running.collectAsStateWithLifecycle()
    val error by FloatingMonitorService.error.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var settingsError by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Settings.canDrawOverlays(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    InfoCard("浮動監控") {
        GalaxyText("在其他 App 上方顯示電池淨功率、電池溫度和 CPU 頻率。可拖曳位置。", color = Muted)
        when {
            running -> GalaxyOutlinedButton("停止浮動監控", { FloatingMonitorService.stop(context) })
            allowed -> GalaxyPrimaryTextButton("開啟浮動監控", { FloatingMonitorService.start(context) })
            else -> GalaxyPrimaryTextButton("允許顯示在其他 App 上層", {
                settingsError = runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.fromParts("package", context.packageName, null)))
                }.isFailure
            })
        }
        if (settingsError) GalaxyText("無法開啟浮動視窗權限設定。", color = GalaxyPalette.error)
        error?.let { GalaxyText(it, color = GalaxyPalette.error) }
    }
}

@Composable internal fun PerformanceNotificationCard() {
    val context = LocalContext.current
    val running by PerformanceNotificationService.running.collectAsStateWithLifecycle()
    val error by PerformanceNotificationService.error.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PerformanceNotificationService.start(context)
        else PerformanceNotificationService.error.value = "請到通知設定允許顯示。"
    }
    InfoCard("效能通知") {
        GalaxyText("在通知欄顯示 CPU 頻率、記憶體用量與電池溫度。", color = Muted)
        if (running) GalaxyOutlinedButton("停止效能通知", { PerformanceNotificationService.stop(context) })
        else GalaxyPrimaryTextButton("開啟效能通知", {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else PerformanceNotificationService.start(context)
        })
        error?.let { GalaxyText(it, color = GalaxyPalette.error) }
    }
}
