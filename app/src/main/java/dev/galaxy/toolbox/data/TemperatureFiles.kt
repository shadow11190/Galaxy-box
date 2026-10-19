package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.*
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Paths

data class TemperatureFileResult(val raw: String?, val status: TemperatureReadStatus, val detail: String = "")
data class TemperatureDirectoryResult(val paths: List<String>, val status: TemperatureReadStatus, val detail: String = "")
interface TemperatureFiles {
    fun read(path: String): TemperatureFileResult
    fun list(path: String): TemperatureDirectoryResult
    fun canonical(path: String): String
}
open class NioTemperatureFiles : TemperatureFiles {
    override fun read(path: String): TemperatureFileResult = try {
        Files.newInputStream(Paths.get(path)).use { input ->
            val bytes = ByteArray(4096)
            val count = input.read(bytes)
            val raw = if (count <= 0) "" else String(bytes, 0, count, Charsets.UTF_8).trim()
            TemperatureFileResult(raw, if (raw.isEmpty()) TemperatureReadStatus.NO_DATA else TemperatureReadStatus.OK)
        }
    } catch (error: Exception) { TemperatureFileResult(null, fileFailure(error), error.javaClass.simpleName) }
    override fun list(path: String): TemperatureDirectoryResult = try {
        Files.newDirectoryStream(Paths.get(path)).use { stream -> TemperatureDirectoryResult(stream.take(1024).map { it.toString() }, TemperatureReadStatus.OK) }
    } catch (error: Exception) { TemperatureDirectoryResult(emptyList(), fileFailure(error), error.javaClass.simpleName) }
    override fun canonical(path: String): String = runCatching { Paths.get(path).toRealPath().toString() }.getOrElse { Paths.get(path).toAbsolutePath().normalize().toString() }
    companion object {
        internal fun fileFailure(error: Exception) = when (error) {
            is AccessDeniedException, is SecurityException -> TemperatureReadStatus.PERMISSION_DENIED
            is NoSuchFileException, is NotDirectoryException -> TemperatureReadStatus.NOT_FOUND
            else -> TemperatureReadStatus.UNAVAILABLE
        }
        // Linux errno values are stable on Android. Unknown errors are never guessed from error text.
        internal fun errnoStatus(errno: Int) = when (errno) {
            1, 13 -> TemperatureReadStatus.PERMISSION_DENIED // EPERM, EACCES
            2, 20 -> TemperatureReadStatus.NOT_FOUND // ENOENT, ENOTDIR
            11, 61 -> TemperatureReadStatus.NO_DATA // EAGAIN, ENODATA
            else -> TemperatureReadStatus.UNAVAILABLE
        }
    }
}
object TemperatureConversion {
    fun observation(descriptor: TemperatureDescriptor, result: TemperatureFileResult, atMs: Long,
        overrideStatus: TemperatureReadStatus? = null, explanation: String = ""): TemperatureReading {
        val raw = result.raw?.substringBefore('\n')?.trim()
        val number = if (descriptor.declaredUnit == TemperatureUnit.CELSIUS) raw?.toDoubleOrNull()?.takeIf { it.isFinite() }
            else raw?.toLongOrNull()?.toDouble()
        val status = overrideStatus ?: if (result.status == TemperatureReadStatus.OK && number == null) TemperatureReadStatus.UNPARSABLE else result.status
        val celsius = if (status == TemperatureReadStatus.OK && descriptor.classification.kind in listOf(TemperatureKind.MEASUREMENT, TemperatureKind.THRESHOLD))
            descriptor.declaredUnit.divisor?.let { divisor -> number?.div(divisor) } else null
        return TemperatureReading(descriptor, raw, celsius, status, atMs, if (status == TemperatureReadStatus.OK) atMs else null,
            if (status == TemperatureReadStatus.OK) celsius else null, listOf(result.detail, explanation).filter { it.isNotBlank() }.joinToString("；"))
    }
}
