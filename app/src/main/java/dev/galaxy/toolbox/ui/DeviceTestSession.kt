package dev.galaxy.toolbox.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.galaxy.toolbox.data.ConnectionDetails
import dev.galaxy.toolbox.diagnostics.DeviceTest
import dev.galaxy.toolbox.model.BatteryPresentation
import dev.galaxy.toolbox.model.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable internal fun DeviceTestSession(test: DeviceTest, snapshot: Snapshot, remaining: Int, onResult: (String) -> Unit, onClose: () -> Unit) {
    var visual by remember(test) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        if (visual != null) ScreenTool(visual!!) { visual = null }
        else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(test.title, style = MaterialTheme.typography.headlineMedium)
                Text("結果由你手動判讀 · 剩餘 $remaining 項", color = Muted, style = MaterialTheme.typography.bodySmall)
                when (test) {
                    DeviceTest.SYSTEM -> InfoCard("系統回報") {
                        Text("${Build.MANUFACTURER} ${Build.MODEL}"); Text("Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
                        Text("安全性更新 ${Build.VERSION.SECURITY_PATCH}"); Text(Build.DISPLAY)
                    }
                    DeviceTest.BATTERY -> BatteryPresentation.visible(snapshot.battery).forEach { ReadingCard(it) }
                    DeviceTest.CHARGING -> {
                        Text("接上或移除充電配件，觀察系統回報是否改變。")
                        BatteryPresentation.visible(snapshot.battery.filter { it.label in listOf("電源來源", "充放電狀態", "電壓", "瞬時電流") }).forEach { ReadingCard(it) }
                    }
                    DeviceTest.WIFI, DeviceTest.MOBILE -> ConnectionTest(test)
                    DeviceTest.SIM -> SimTest()
                    DeviceTest.BLUETOOTH -> BluetoothTest()
                    DeviceTest.HEADPHONES -> HeadphoneTest()
                    DeviceTest.NFC -> NfcTest()
                    DeviceTest.LOCATION -> LocationAccuracyTest()
                    DeviceTest.SENSORS -> SensorCard()
                    DeviceTest.PROXIMITY -> { Text("遮住與移開手機的距離感測區，觀察距離回報。部分裝置只有遠／近兩種數值。") ; SensorCard() }
                    DeviceTest.KEYS -> VolumeKeyTest()
                    DeviceTest.VIBRATION -> VibrationCard()
                    DeviceTest.SPEAKER -> SpeakerTest()
                    DeviceTest.MICROPHONE -> MicrophoneTest()
                    DeviceTest.BIOMETRIC -> BiometricTest()
                    DeviceTest.STYLUS -> StylusTest()
                    DeviceTest.DISPLAY, DeviceTest.TOUCH -> {
                        Text(if (test == DeviceTest.DISPLAY) "逐一檢視白、黑、紅、綠、藍、灰；自行觀察亮暗點與色彩。" else "拖曳與多指觸控，觀察軌跡及同時觸控數。")
                        Button(onClick = { visual = if (test == DeviceTest.DISPLAY) "螢幕" else "觸控" }) { Text("開始全螢幕檢查") }
                    }
                    DeviceTest.CAMERA -> PermissionGate(listOf(Manifest.permission.CAMERA), "相機拍攝") {
                        val context = LocalContext.current; var error by remember { mutableStateOf<String?>(null) }
                        Text("開啟系統相機，自行確認預覽與拍照，再返回記錄結果。")
                        Button(onClick = { runCatching { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }.onFailure { error = "系統未提供可開啟的相機頁面" } }) { Text("開啟系統相機") }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                HorizontalDivider()
                Text("以下是你的觀察紀錄，不是自動硬體認證。", color = Muted, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onResult("使用者確認正常") }, modifier = Modifier.fillMaxWidth()) { Text("我確認正常") }
                OutlinedButton(onClick = { onResult("使用者觀察到問題") }, modifier = Modifier.fillMaxWidth()) { Text("我觀察到問題") }
                TextButton(onClick = { onResult("略過／未判讀") }) { Text("略過此項") }
                TextButton(onClick = onClose) { Text("結束檢查") }
            }
        }
    }
}

@Composable internal fun PermissionGate(permissions: List<String>, name: String, any: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun allowed(): Boolean = permissions.map { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }.let { if (any) it.any { n -> n } else it.all { n -> n } }
    var granted by remember(permissions) { mutableStateOf(allowed()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = allowed() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, permissions) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = allowed() }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (granted) content() else {
        Text("$name 需要你允許相關權限，拒絕可略過此項。", color = Muted)
        Button(onClick = { request.launch(permissions.toTypedArray()) }) { Text("允許 $name 權限") }
        TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) } }) { Text("開啟 App 權限設定") }
    }
}

@Composable private fun ConnectionTest(test: DeviceTest) {
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    var rows by remember { mutableStateOf(emptyList<dev.galaxy.toolbox.model.Reading>()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
            val read = withContext(Dispatchers.IO) { runCatching { ConnectionDetails.network(context) } }
            rows = read.getOrDefault(emptyList()); error = read.exceptionOrNull()?.let { "連線資料無法取得" }; delay(2000)
        }
    } }
    rows.filter { it.label in listOf("目前網路", "網際網路狀態", "計量網路", "Wi-Fi 連線速率", "Wi-Fi 頻率") }.forEach { ReadingCard(it) }
    Text("這是連線狀態檢查，未執行網際網路測速。", color = Muted, style = MaterialTheme.typography.bodySmall)
    Button(onClick = { runCatching { context.startActivity(Intent(if (test == DeviceTest.WIFI) Settings.ACTION_WIFI_SETTINGS else Settings.ACTION_WIRELESS_SETTINGS)) }.onFailure { error = "無法開啟設定" } }) { Text("開啟連線設定") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable private fun SimTest() {
    val context = LocalContext.current; var refresh by remember { mutableIntStateOf(0) }
    val result = remember(refresh) { runCatching {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val count = if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else @Suppress("DEPRECATION") tm.phoneCount
        (0 until count.coerceIn(0, 8)).map { slot -> "SIM ${slot + 1} · " + when (tm.getSimState(slot)) {
            TelephonyManager.SIM_STATE_ABSENT -> "未插入"; TelephonyManager.SIM_STATE_READY -> "就緒"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "需要 PIN"; TelephonyManager.SIM_STATE_PUK_REQUIRED -> "需要 PUK"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "網路鎖定"; TelephonyManager.SIM_STATE_NOT_READY -> "尚未就緒"
            else -> "狀態碼 ${tm.getSimState(slot)}"
        } }
    } }
    result.getOrNull()?.forEach { Text(it) }
    if (result.isFailure) Text("系統未開放 SIM 狀態", color = Muted)
    Text("只讀公開插槽狀態，不讀電話號碼、IMEI 或 SIM 序號。", color = Muted, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { refresh++ }) { Text("重新讀取") }
}

@Composable private fun VolumeKeyTest() {
    val focus = remember { FocusRequester() }; var up by remember { mutableIntStateOf(0) }; var down by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).focusRequester(focus).onPreviewKeyEvent {
        val event = it.nativeKeyEvent
        if (event.keyCode in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) { if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) up++ else down++ }
            true
        } else false
    }.focusable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("音量＋：$up 次　音量－：$down 次")
        Text("按下實體音量鍵。電源、Home 與系統側鍵不在一般 App 可完整攔截的範圍。", color = Muted)
        TextButton(onClick = { focus.requestFocus() }) { Text("開始接收音量按鍵") }
    }
}

@Composable private fun BiometricTest() {
    if (Build.VERSION.SDK_INT < 28) return
    val context = LocalContext.current; val owner = LocalLifecycleOwner.current
    var message by remember { mutableStateOf("由系統選擇可用的指紋或臉部方式；本應用不讀取生物特徵。") }
    var cancel by remember { mutableStateOf<CancellationSignal?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel?.cancel() }
        owner.lifecycle.addObserver(observer); onDispose { cancel?.cancel(); owner.lifecycle.removeObserver(observer) }
    }
    Text(message)
    Button(onClick = {
        runCatching {
            cancel?.cancel(); val signal = CancellationSignal(); cancel = signal
            val builder = BiometricPrompt.Builder(context).setTitle("Galaxy box 生物辨識檢查")
                .setSubtitle("僅驗證本機辨識流程")
                .setNegativeButton("取消", context.mainExecutor) { _, _ -> signal.cancel() }
            if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            builder.build().authenticate(signal, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { message = "系統回報辨識成功；未確認具體使用哪一種生物辨識。" }
                override fun onAuthenticationFailed() { message = "本次未匹配，可重試；不等同硬體故障。" }
                override fun onAuthenticationError(code: Int, text: CharSequence?) { message = "系統回覆 $code：${text?.toString().orEmpty()}" }
            })
        }.onFailure { message = "無法啟動系統辨識：${it.javaClass.simpleName}" }
    }) { Text("開始系統生物辨識") }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable private fun StylusTest() {
    var status by remember { mutableStateOf("請用觸控筆在下方區域畫線。") }
    var points by remember { mutableStateOf(emptyList<Offset>()) }
    Text(status)
    Canvas(Modifier.fillMaxWidth().height(280.dp).background(Panel).pointerInteropFilter { e ->
        val i = e.actionIndex.coerceIn(0, e.pointerCount - 1)
        if (e.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) {
            status = String.format(Locale.ROOT, "筆輸入 · 壓力 %.3f · 傾角 %.3f rad · 按鈕 %d", e.getPressure(i), e.getAxisValue(MotionEvent.AXIS_TILT, i), e.buttonState)
            points = (points + Offset(e.getX(i), e.getY(i))).takeLast(1800); true
        } else false
    }) { points.forEach { drawCircle(Color(0xFF76E1C3), 3.dp.toPx(), it) } }
    Text("MotionEvent 實際回報；不宣稱支援 S Pen 藍牙遙控或特定壓感級數。", color = Muted, style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { points = emptyList() }) { Text("清除筆跡") }
}
