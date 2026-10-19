package dev.galaxy.toolbox.diagnostics

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ShizukuConnection(val title: String) {
    NOT_RUNNING("Shizuku 未連線"), TOO_OLD("需要 Shizuku 13 以上"), NOT_ADB("請使用 ADB 模式啟動 Shizuku"),
    NEED_PERMISSION("Shizuku 已連線，尚未授權"), READY("已連接 Shizuku · ADB 模式"), FAILED("無法取得 Shizuku 狀態")
}
data class ShizukuAvailability(val connection: ShizukuConnection = ShizukuConnection.NOT_RUNNING, val permissionBlocked: Boolean = false)

class ShizukuDiagnosticsClient(context: Context, private val permissionResult: (Boolean) -> Unit) : AutoCloseable {
    private val mutable = MutableStateFlow(ShizukuAvailability())
    val availability = mutable.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val args = Shizuku.UserServiceArgs(ComponentName(context, DiagnosticUserService::class.java))
        .tag("readonly-diagnostics").processNameSuffix("readonly_diagnostics").daemon(false).version(13)
    private var active: ServiceConnection? = null
    private val received = Shizuku.OnBinderReceivedListener { refresh() }
    private val dead = Shizuku.OnBinderDeadListener { disconnect(); mutable.value = ShizukuAvailability() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == REQUEST_CODE) { refresh(); permissionResult(grant == PackageManager.PERMISSION_GRANTED) }
    }
    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        refresh()
    }
    fun refresh(): ShizukuAvailability {
        val next = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuAvailability()
                Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> ShizukuAvailability(ShizukuConnection.TOO_OLD)
                Shizuku.getUid() != 2000 -> ShizukuAvailability(ShizukuConnection.NOT_ADB)
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuAvailability(ShizukuConnection.READY)
                else -> ShizukuAvailability(ShizukuConnection.NEED_PERMISSION, Shizuku.shouldShowRequestPermissionRationale())
            }
        }.getOrElse { ShizukuAvailability(ShizukuConnection.FAILED) }
        mutable.value = next
        return next
    }
    fun requestPermission() { Shizuku.requestPermission(REQUEST_CODE) }

    suspend fun collect(kind: ProbeKind): PrivilegedSnapshot = withTimeout(15_000) {
        check(refresh().connection == ShizukuConnection.READY) { "Shizuku 尚未授權或已斷線" }
        try {
            val remote = suspendCancellableCoroutine<IReadOnlyDiagnostics> { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (continuation.isActive) {
                            if (binder == null || !binder.pingBinder()) continuation.resumeWithException(IllegalStateException("讀取服務未連線"))
                            else continuation.resume(IReadOnlyDiagnostics.Stub.asInterface(binder))
                        }
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("讀取服務已中斷"))
                    }
                }
                active = connection
                continuation.invokeOnCancellation { disconnect() }
                try { Shizuku.bindUserService(args, connection) }
                catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
            // Binder calls are blocking. Cancellation explicitly destroys the short-lived service,
            // so a timeout/background transition can interrupt a blocked remote call.
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { disconnect() }
                worker.execute {
                    try {
                        val text = when (kind) { ProbeKind.BATTERY -> remote.collectBattery(); ProbeKind.GPU -> remote.collectGpu() }
                        val parsed = PrivilegedSnapshotParser.parse(text, kind)
                        if (continuation.isActive) continuation.resume(parsed)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            }
        } finally { disconnect() }
    }
    fun disconnect() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { disconnect() }; return }
        val connection = active ?: return
        active = null
        runCatching { Shizuku.unbindUserService(args, connection, true) }
    }
    override fun close() {
        disconnect(); Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission); worker.shutdownNow()
    }
    companion object { private const val REQUEST_CODE = 6601 }
}
