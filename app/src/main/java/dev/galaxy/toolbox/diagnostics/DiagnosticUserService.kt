package dev.galaxy.toolbox.diagnostics

import android.content.Context
import android.os.Binder
import android.os.Process
import dev.galaxy.toolbox.data.AndroidTemperatureFiles
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Launched by Shizuku, never an exported Android Service. No caller-controlled commands or paths. */
class DiagnosticUserService(context: Context) : IReadOnlyDiagnostics.Stub() {
    private val ownerUid = context.applicationInfo.uid
    @Volatile private var active: java.lang.Process? = null
    override fun destroy() {
        require(Binder.getCallingUid() == ownerUid || Binder.getCallingUid() == 2000)
        active?.destroyForcibly(); exitProcess(0)
    }
    @Synchronized override fun collectBattery(): String = collect(ProbeKind.BATTERY)
    @Synchronized override fun collectGpu(): String = collect(ProbeKind.GPU)
    private fun collect(kind: ProbeKind): String {
        check(Process.myUid() == 2000) { "ADB 模式才可讀取" }
        check(Binder.getCallingUid() == ownerUid) { "呼叫來源不符" }
        val reader = AndroidTemperatureFiles()
        val files = JSONArray()
        for (spec in ProbeContracts.files(kind)) {
            val result = reader.read(spec.path)
            files.put(JSONObject().put("path", spec.path).put("raw", result.raw ?: JSONObject.NULL).put("status", result.status.name))
        }
        val response = JSONObject().put("schema", 1).put("kind", kind.name).put("uid", Process.myUid())
            .put("at", System.currentTimeMillis()).put("files", files)
        if (kind == ProbeKind.BATTERY) response.put("dump", batteryDump())
        return response.toString()
    }
    private fun batteryDump(): JSONObject {
        val worker = Executors.newSingleThreadExecutor()
        return try {
            val process = ProcessBuilder(ProbeContracts.batteryCommand).redirectErrorStream(true).start()
            active = process
            val text = worker.submit<String> {
                process.inputStream.bufferedReader().use { input ->
                    val out = StringBuilder(); val buffer = CharArray(2048)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        check(out.length + n <= 48 * 1024) { "OUTPUT_LIMIT" }
                        out.append(buffer, 0, n)
                    }
                    out.toString()
                }
            }
            if (!process.waitFor(7, TimeUnit.SECONDS)) { process.destroyForcibly(); JSONObject().put("status", "TIMEOUT") }
            else if (process.exitValue() != 0) JSONObject().put("status", "COMMAND_FAILED（exit=${process.exitValue()}）")
            else JSONObject().put("status", "OK").put("text", text.get(1, TimeUnit.SECONDS))
        } catch (_: Exception) { JSONObject().put("status", "UNAVAILABLE_OR_OUTPUT_LIMIT") }
        finally { active?.destroyForcibly(); active = null; worker.shutdownNow() }
    }
}
