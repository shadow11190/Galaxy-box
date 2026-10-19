package dev.galaxy.toolbox.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal fun CameraAccessCard(busy: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(allowed()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; onRefresh()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = allowed()
                if (now != granted) { granted = now; onRefresh() }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    InfoCard("相機資料存取") {
        Text(if (granted) "已允許相機資訊存取" else "部分詳細欄位需要相機權限；不允許也可查看公開資料。", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("僅讀取相機中繼資料，不啟動拍攝。", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = !busy, onClick = { if (granted) onRefresh() else request.launch(Manifest.permission.CAMERA) }) {
            Text(if (busy) "讀取中…" else if (granted) "重新讀取相機資訊" else "允許讀取完整相機資訊")
        }
    }
}
