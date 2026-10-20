package dev.galaxy.toolbox

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.galaxy.toolbox.data.BugreportDocument
import dev.galaxy.toolbox.data.ImportProgress
import kotlinx.coroutines.flow.update
import dev.galaxy.toolbox.data.FirmwareRepository
import dev.galaxy.toolbox.data.HardwareRepository
import dev.galaxy.toolbox.data.TemperatureHistory
import dev.galaxy.toolbox.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import android.os.SystemClock
import dev.galaxy.toolbox.widgets.WidgetUpdates
import dev.galaxy.toolbox.diagnostics.*

data class ToolboxState(
    val snapshot: Snapshot = Snapshot(),
    val hardware: List<Reading> = emptyList(),
    val cameraBusy: Boolean = false,
    val shizuku: ShizukuAvailability = ShizukuAvailability(),
    val privilegedBusy: Boolean = false,
    val privilegedKind: ProbeKind? = null,
    val privilegedBattery: PrivilegedSnapshot? = null,
    val privilegedGpu: PrivilegedSnapshot? = null,
    val privilegedError: String? = null,
    val history: List<Float> = emptyList(),
    val report: ImportedReport? = null,
    val importBusy: Boolean = false,
    val importError: String? = null,
    val importProgress: ImportProgress? = null,
    val firmware: FirmwareResult? = null,
    val firmwareBusy: Boolean = false,
    val firmwareError: String? = null,
    val monitorError: String? = null,
    val temperatures: List<TemperatureReading> = emptyList(),
    val temperatureHistory: Map<String, List<TemperaturePoint>> = emptyMap(),
    val temperatureNowMs: Long = System.currentTimeMillis()
)

class ToolboxViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HardwareRepository(application)
    private val mutable = MutableStateFlow(ToolboxState())
    val state = mutable.asStateFlow()
    private var privilegedJob: Job? = null
    private var pendingPrivileged: ProbeKind? = null
    private var permissionPending: ProbeKind? = null
    private val shizukuClient = ShizukuDiagnosticsClient(application) { granted ->
        val kind = permissionPending; permissionPending = null
        if (granted && kind != null) readPrivileged(kind)
        else if (!granted) mutable.update { it.copy(privilegedError = "未授權；一般模式仍可使用。") }
    }
    private var staticJob: Job? = null
    private var cameraJob: Job? = null
    private var monitor: Job? = null
    private var importJob: Job? = null
    private var activeDocument: BugreportDocument? = null
    private var importGeneration = 0L
    private var firmwareJob: Job? = null
    private var lastWidgetUpdate = -30_000L
    private val temperatureHistory = TemperatureHistory()
    private val temperatureHistoryMutex = Mutex()
    private var temperatureVisible = false
    init {
        viewModelScope.launch { shizukuClient.availability.collect { next -> mutable.update { it.copy(shizuku = next) } } }
        staticJob = viewModelScope.launch {
            val hardware = withContext(Dispatchers.IO) {
                runCatching { repository.staticHardware() }.getOrElse {
                    listOf(Reading("硬體資訊", null, "公開 API", "讀取失敗：${it.message}"))
                }
            }
            mutable.update { it.copy(hardware = hardware) }
        }
    }
    fun refreshShizuku() { shizukuClient.refresh() }
    fun requestPrivileged(kind: ProbeKind) {
        if (privilegedJob?.isActive == true) return
        mutable.update { it.copy(privilegedKind = kind, privilegedError = null) }
        val availability = shizukuClient.refresh()
        if (availability.connection == ShizukuConnection.READY) readPrivileged(kind)
        else if (availability.connection == ShizukuConnection.NEED_PERMISSION && !availability.permissionBlocked) {
            permissionPending = kind
            runCatching { shizukuClient.requestPermission() }.onFailure {
                permissionPending = null; mutable.update { old -> old.copy(privilegedError = "無法請求 Shizuku 授權，請確認服務狀態。") }
            }
        }
    }
    private fun readPrivileged(kind: ProbeKind) {
        if (monitor?.isActive != true) { pendingPrivileged = kind; return }
        pendingPrivileged = null
        if (privilegedJob?.isActive == true) return
        privilegedJob = viewModelScope.launch {
            mutable.update { it.copy(privilegedBusy = true, privilegedKind = kind, privilegedError = null) }
            try {
                val result = shizukuClient.collect(kind)
                mutable.update { if (kind == ProbeKind.BATTERY) it.copy(privilegedBattery = result) else it.copy(privilegedGpu = result) }
            } catch (e: TimeoutCancellationException) { mutable.update { it.copy(privilegedError = "讀取逾時；已停止讀取服務。") } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(privilegedError = "讀取失敗：${e.message ?: e.javaClass.simpleName}；保留上次快照。") } }
            finally { mutable.update { it.copy(privilegedBusy = false) } }
        }
    }
    fun cancelPrivileged() { privilegedJob?.cancel(); shizukuClient.disconnect() }
    fun refreshConnectionDetails(category: HardwareCategory) {
        if (category !in listOf(HardwareCategory.NETWORK, HardwareCategory.AUDIO)) return
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) {
                runCatching { if (category == HardwareCategory.NETWORK) dev.galaxy.toolbox.data.ConnectionDetails.network(getApplication())
                    else dev.galaxy.toolbox.data.ConnectionDetails.audio(getApplication()) }.getOrElse { emptyList() }
            }
            mutable.update { old -> old.copy(hardware = old.hardware.filterNot { HardwareCategory.of(it) == category } + details) }
        }
    }
    fun refreshCameras() {
        if (cameraJob?.isActive == true) return
        cameraJob = viewModelScope.launch {
            mutable.update { it.copy(cameraBusy = true) }
            try {
                staticJob?.join()
                val cameras = withContext(Dispatchers.IO) {
                    runCatching {
                        dev.galaxy.toolbox.data.CameraDetails.read(getApplication<Application>().getSystemService(android.hardware.camera2.CameraManager::class.java))
                    }.getOrElse { listOf(Reading("讀取狀態", null, "CameraManager", "無法取得（${it.javaClass.simpleName}）", HardwareCategory.CAMERA, "相機資訊")) }
                }
                mutable.update { old -> old.copy(hardware = old.hardware.filterNot { HardwareCategory.of(it) == HardwareCategory.CAMERA } + cameras) }
            } finally { mutable.update { it.copy(cameraBusy = false) } }
        }
    }
    fun startMonitoring() {
        if (monitor?.isActive == true) return
        pendingPrivileged?.let { kind -> viewModelScope.launch { yield(); readPrivileged(kind) } }
        monitor = viewModelScope.launch {
            while (isActive) {
                try {
                    val pollTemperatures = temperatureVisible
                    val rawNext = withContext(Dispatchers.IO) { repository.sample(includeTemperatures = pollTemperatures) }
                    val next = rawNext.copy(systemThermal = rawNext.systemThermal.copy(lastSuccessAtMs =
                        rawNext.systemThermal.lastSuccessAtMs ?: mutable.value.snapshot.systemThermal.lastSuccessAtMs))
                    val temperatureNow = System.currentTimeMillis()
                    val (readings, series) = withContext(Dispatchers.Default) {
                        temperatureHistoryMutex.withLock {
                            temperatureHistory.update(next.temperatureReadings, temperatureNow, next.temperatureFilesPolled)
                            temperatureHistory.readings() to temperatureHistory.series()
                        }
                    }
                    mutable.value = mutable.value.copy(snapshot = next, monitorError = null,
                        history = (mutable.value.history + listOfNotNull(next.memoryUsed)).takeLast(60),
                        temperatures = readings, temperatureHistory = series, temperatureNowMs = temperatureNow)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastWidgetUpdate >= 30_000L) {
                        withContext(Dispatchers.IO) { runCatching { WidgetUpdates.publishAll(getApplication(), next) } }
                        lastWidgetUpdate = now
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val now = System.currentTimeMillis()
                    val previous = mutable.value.temperatures
                    val (readings, series) = withContext(Dispatchers.Default) {
                        temperatureHistoryMutex.withLock {
                            temperatureHistory.update(previous.map { it.copy(raw = null, celsius = null, status = TemperatureReadStatus.UNAVAILABLE, attemptedAtMs = now) }, now)
                            temperatureHistory.readings() to temperatureHistory.series()
                        }
                    }
                    mutable.value = mutable.value.copy(monitorError = "取樣失敗：${e.message}", temperatures = readings,
                        temperatureHistory = series, temperatureNowMs = now)
                }
                delay(2000)
            }
        }
    }
    fun stopMonitoring() { monitor?.cancel(); monitor = null; cancelPrivileged() }
    fun rescanGpuSources() { repository.rescanGpuSources(); repository.rescanTemperatureSources() }
    fun rescanTemperatureSources() { repository.rescanTemperatureSources() }
    fun setTemperatureVisible(visible: Boolean) { temperatureVisible = visible }
    fun importReport(uri: Uri) {
        if (importJob?.isActive == true) return
        val generation = ++importGeneration
        val document = BugreportDocument(getApplication())
        activeDocument = document
        mutable.update { it.copy(importBusy = true, importError = null, report = null, importProgress = ImportProgress("準備開啟 ZIP／TXT")) }
        importJob = viewModelScope.launch {
            try {
                val fields = withContext(Dispatchers.IO) {
                    document.readReport(uri) { progress ->
                        viewModelScope.launch { if (generation == importGeneration) mutable.update { it.copy(importProgress = progress) } }
                    }
                }
                if (generation == importGeneration) mutable.update { it.copy(importBusy = false, importProgress = null,
                    report = ImportedReport("使用者選取的完整報告（ZIP／TXT／LOG）", fields.fields, Instant.now().toString(), fields.diagnostics)) }
            } catch (e: CancellationException) {
                if (generation == importGeneration) mutable.update { it.copy(importBusy = false, importProgress = null, importError = "已取消匯入") }
                throw e
            } catch (e: Exception) {
                if (generation == importGeneration) mutable.update { it.copy(importBusy = false, importProgress = null,
                    importError = "匯入失敗：${e.message ?: e.javaClass.simpleName}。若來源是雲端，請先下載到手機再選取。") }
            } finally { if (generation == importGeneration) activeDocument = null }
        }
    }
    fun cancelImport() {
        ++importGeneration
        importJob?.cancel(); activeDocument?.cancel(); activeDocument = null
        mutable.update { it.copy(importBusy = false, importProgress = null, importError = "已取消匯入") }
    }
    override fun onCleared() { activeDocument?.cancel(); shizukuClient.close(); super.onCleared() }
    fun clearReport() { mutable.value = mutable.value.copy(report = null, importError = null) }
    fun queryFirmware(model: String, csc: String) {
        if (firmwareJob?.isActive == true) return
        val query = try { FirmwareQuery.create(model, csc) }
        catch (e: Exception) { mutable.value = mutable.value.copy(firmware = null, firmwareError = e.message); return }
        mutable.value = mutable.value.copy(firmwareBusy = true, firmwareError = null, firmware = null)
        firmwareJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { FirmwareRepository().fetch(query) }
                mutable.value = mutable.value.copy(firmwareBusy = false, firmware = result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(firmwareBusy = false, firmwareError = "${query.model} / ${query.csc}：${e.message}") }
        }
    }
}
