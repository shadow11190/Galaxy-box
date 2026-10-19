package dev.galaxy.toolbox.data

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.os.Build
import dev.galaxy.toolbox.model.TemperatureReadStatus
import java.io.FileDescriptor

/** Called only on the IO worker; Android errno distinguishes permission, missing source and no-data. */
class AndroidTemperatureFiles : NioTemperatureFiles() {
    override fun read(path: String): TemperatureFileResult {
        var descriptor: FileDescriptor? = null
        return try {
            val flags = OsConstants.O_RDONLY or if (Build.VERSION.SDK_INT >= 27) OsConstants.O_CLOEXEC else 0
            descriptor = Os.open(path, flags, 0)
            val bytes = ByteArray(4096)
            val count = Os.read(descriptor, bytes, 0, bytes.size)
            val raw = if (count <= 0) "" else String(bytes, 0, count, Charsets.UTF_8).trim()
            TemperatureFileResult(raw, if (raw.isEmpty()) TemperatureReadStatus.NO_DATA else TemperatureReadStatus.OK)
        } catch (error: ErrnoException) {
            TemperatureFileResult(null, errnoStatus(error.errno), "errno=${error.errno}")
        } catch (_: SecurityException) { TemperatureFileResult(null, TemperatureReadStatus.PERMISSION_DENIED, "SecurityException") }
        catch (_: Exception) { TemperatureFileResult(null, TemperatureReadStatus.UNAVAILABLE) }
        finally { descriptor?.let { runCatching { Os.close(it) } } }
    }
}
