package dev.galaxy.toolbox.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.galaxy.toolbox.charging.ChargingNotification

@Composable internal fun ChargingNotificationCard() {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    val running by ChargingNotification.running.collectAsStateWithLifecycle()
    val error by ChargingNotification.error.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(ChargingNotification.allowed(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = ChargingNotification.allowed(context)
        if (granted && allowed) ChargingNotification.enable(context) else message = "通知未允許，可到通知設定開啟。"
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = ChargingNotification.allowed(context)
        } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    InfoCard("狀態列充電通知") {
        GalaxyText(if (running) "監控中" else "未開啟", color = Mint)
        GalaxyText("顯示充電狀態、電量與電池淨功率。", color = Muted)
        if (running) GalaxyOutlinedButton(onClick = { ChargingNotification.disable(context) }) { GalaxyText("停止充電通知") }
        else GalaxyPrimaryButton(onClick = {
            message = null
            if (Build.VERSION.SDK_INT >= 33 && !allowed) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else if (allowed) ChargingNotification.enable(context) else message = "請到通知設定允許顯示。"
        }) { GalaxyText("開啟充電通知") }
        if (!allowed) GalaxyTextButton(onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
                .onFailure { message = "無法開啟通知設定" }
        }) { GalaxyText("開啟通知設定") }
        message?.let { GalaxyText(it, color = Muted) }; error?.let { GalaxyText(it, color = GalaxyPalette.error) }
    }
}
