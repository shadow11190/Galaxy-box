package dev.galaxy.toolbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal enum class BackgroundFill(val label: String) {
    COVER("覆蓋"), CONTAIN("適應"), STRETCH("拉伸"), CENTER("置中"), TILE("平鋪")
}

internal enum class BackgroundAspect(val label: String, val ratio: Float?) {
    FREE("自由比例", null), WIDE("16:9", 16f / 9f), TEN("16:10", 1.6f), FOUR_THREE("4:3", 4f / 3f), SQUARE("1:1", 1f), SCREEN("符合螢幕", null)
}

internal data class BackgroundPreset(
    val id: String,
    val name: String,
    val fileName: String,
    val format: String,
    val fill: BackgroundFill = BackgroundFill.COVER,
    val aspect: BackgroundAspect = BackgroundAspect.SCREEN,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val rotation: Float = 0f,
    val blur: Float = 0f,
    val brightness: Float = 1f,
    val overlay: Float = .28f,
    val luminance: Float = .5f,
)

internal data class CustomBackgroundState(
    val enabled: Boolean = false,
    val activeId: String? = null,
    val presets: List<BackgroundPreset> = emptyList(),
) {
    val active: BackgroundPreset? get() = presets.firstOrNull { it.id == activeId }
    fun replace(preset: BackgroundPreset) = copy(presets = presets.map { if (it.id == preset.id) preset else it })
}

internal data class BackgroundMedia(val bitmap: Bitmap?, val movie: Movie?, val width: Int, val height: Int, val luminance: Float)

/** Images stay in private files; preferences contain only bounded parameters and relative file names. */
internal object BackgroundStore {
    private const val PREFS = "custom_background"
    private const val KEY = "state"
    private const val MAX_BYTES = 20L * 1024 * 1024
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "avif", "bmp", "svg", "heic", "heif", "gif")

    fun load(context: Context): CustomBackgroundState = runCatching {
        val root = JSONObject(context.getSharedPreferences(PREFS, 0).getString(KEY, "{}") ?: "{}")
        val items = root.optJSONArray("presets") ?: JSONArray()
        val presets = (0 until items.length()).mapNotNull { index ->
            runCatching {
                val item = items.getJSONObject(index)
                BackgroundPreset(
                    id = item.getString("id"), name = item.getString("name"), fileName = item.getString("file"), format = item.getString("format"),
                    fill = BackgroundFill.entries.firstOrNull { it.name == item.optString("fill") } ?: BackgroundFill.COVER,
                    aspect = BackgroundAspect.entries.firstOrNull { it.name == item.optString("aspect") } ?: BackgroundAspect.SCREEN,
                    zoom = item.optDouble("zoom", 1.0).toFloat().coerceIn(1f, 4f),
                    panX = item.optDouble("panX", 0.0).toFloat().coerceIn(-1f, 1f),
                    panY = item.optDouble("panY", 0.0).toFloat().coerceIn(-1f, 1f),
                    rotation = item.optDouble("rotation", 0.0).toFloat().coerceIn(-180f, 180f),
                    blur = item.optDouble("blur", 0.0).toFloat().coerceIn(0f, 24f),
                    brightness = item.optDouble("brightness", 1.0).toFloat().coerceIn(.25f, 1.5f),
                    overlay = item.optDouble("overlay", .28).toFloat().coerceIn(0f, .8f),
                    luminance = item.optDouble("luminance", .5).toFloat().coerceIn(0f, 1f),
                ).takeIf { File(directory(context), it.fileName).isFile }
            }.getOrNull()
        }
        CustomBackgroundState(root.optBoolean("enabled"), root.optString("active").takeIf(String::isNotEmpty), presets)
    }.getOrDefault(CustomBackgroundState())

    fun save(context: Context, state: CustomBackgroundState) {
        val items = JSONArray()
        state.presets.forEach { p -> items.put(JSONObject().apply {
            put("id", p.id); put("name", p.name); put("file", p.fileName); put("format", p.format)
            put("fill", p.fill.name); put("aspect", p.aspect.name); put("zoom", p.zoom.toDouble())
            put("panX", p.panX.toDouble()); put("panY", p.panY.toDouble()); put("rotation", p.rotation.toDouble())
            put("blur", p.blur.toDouble()); put("brightness", p.brightness.toDouble()); put("overlay", p.overlay.toDouble())
            put("luminance", p.luminance.toDouble())
        }) }
        val root = JSONObject().put("enabled", state.enabled).put("active", state.activeId ?: "").put("presets", items)
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, root.toString()).apply()
    }

    private fun directory(context: Context) = File(context.filesDir, "backgrounds").apply { mkdirs() }
    fun file(context: Context, preset: BackgroundPreset) = File(directory(context), preset.fileName)

    suspend fun import(context: Context, uri: Uri): BackgroundPreset = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val size = if (sizeIndex < 0 || cursor.isNull(sizeIndex)) null else cursor.getLong(sizeIndex)
                    if (size != null && size > MAX_BYTES) throw IllegalArgumentException("圖片太大，請選擇較小的檔案。")
                    if (nameIndex < 0 || cursor.isNull(nameIndex)) null else cursor.getString(nameIndex)
                }
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "background"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri).orEmpty().lowercase()
        val format = when {
            extension in extensions -> extension
            mime == "image/svg+xml" -> "svg"
            mime == "image/gif" -> "gif"
            mime == "image/heic" || mime == "image/heif" -> "heic"
            mime == "image/avif" -> "avif"
            mime == "image/jpeg" -> "jpg"
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            mime == "image/bmp" || mime == "image/x-ms-bmp" -> "bmp"
            else -> throw IllegalArgumentException("不支援此圖片格式或無法解碼。")
        }
        val id = UUID.randomUUID().toString()
        val destination = File(directory(context), "$id.$format")
        try {
            resolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_BYTES) throw IllegalArgumentException("圖片太大，請選擇較小的檔案。")
                    output.write(buffer, 0, count)
                }
            } } ?: throw IllegalArgumentException("無法讀取圖片。")
            if (format == "gif") {
                @Suppress("DEPRECATION") val movie = Movie.decodeFile(destination.path)
                if (movie != null && movie.width().toLong() * movie.height() > 4_000_000) {
                    throw IllegalArgumentException("GIF 尺寸過大，請縮小後再匯入。")
                }
            }
            val decoded = decode(destination, format, 1024) ?: throw IllegalArgumentException("不支援此圖片格式或無法解碼。")
            BackgroundPreset(id, displayName.substringBeforeLast('.').take(40).ifBlank { "背景" }, destination.name, format,
                luminance = decoded.luminance)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    suspend fun duplicate(context: Context, source: BackgroundPreset, name: String): BackgroundPreset = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val copy = source.copy(id = id, fileName = "$id.${source.format}", name = name.trim().take(40).ifBlank { "背景組合" })
        file(context, source).copyTo(file(context, copy), overwrite = false)
        copy
    }

    fun delete(context: Context, state: CustomBackgroundState, id: String): CustomBackgroundState {
        state.presets.firstOrNull { it.id == id }?.let { file(context, it).delete() }
        return state.copy(enabled = if (state.activeId == id) false else state.enabled,
            activeId = state.activeId.takeUnless { it == id }, presets = state.presets.filterNot { it.id == id })
    }

    /** Decoding is bounded to the screen, while the original file is retained for future crops. */
    fun decode(file: File, format: String, maxSide: Int = 2048): BackgroundMedia? = runCatching {
        if (format == "svg") {
            val svg = file.inputStream().use { SVG.getFromInputStream(it) }
            val width = svg.documentWidth.takeIf { it > 0 }?.toInt() ?: maxSide
            val height = svg.documentHeight.takeIf { it > 0 }?.toInt() ?: maxSide
            val factor = minOf(1f, maxSide.toFloat() / maxOf(width, height))
            val bitmap = Bitmap.createBitmap((width * factor).toInt().coerceAtLeast(1), (height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width / width.toFloat(), bitmap.height / height.toFloat())
            svg.renderToCanvas(canvas)
            BackgroundMedia(bitmap, null, bitmap.width, bitmap.height, luminance(bitmap))
        } else if (format == "gif") {
            @Suppress("DEPRECATION") val movie = Movie.decodeFile(file.path) ?: return@runCatching null
            if (movie.width() <= 0 || movie.height() <= 0 || movie.width().toLong() * movie.height() > 4_000_000) return@runCatching null
            val scale = minOf(1f, 128f / maxOf(movie.width(), movie.height()))
            val sample = Bitmap.createBitmap((movie.width() * scale).toInt().coerceAtLeast(1),
                (movie.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sample)
            canvas.scale(scale, scale)
            @Suppress("DEPRECATION") movie.setTime(0)
            @Suppress("DEPRECATION") movie.draw(canvas, 0f, 0f)
            BackgroundMedia(null, movie, movie.width(), movie.height(), luminance(sample))
        } else {
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val size = info.size
                    val factor = minOf(1f, maxSide.toFloat() / maxOf(size.width, size.height))
                    decoder.setTargetSize((size.width * factor).toInt().coerceAtLeast(1), (size.height * factor).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / it <= maxSide }
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            BackgroundMedia(bitmap, null, bitmap.width, bitmap.height, luminance(bitmap))
        }
    }.getOrNull()

    fun thumbnail(context: Context, preset: BackgroundPreset): BackgroundMedia? {
        val media = decode(file(context, preset), preset.format, 128) ?: return null
        if (media.movie == null) return media
        val movie = media.movie
        val scale = minOf(1f, 128f / maxOf(media.width, media.height))
        val bitmap = Bitmap.createBitmap((media.width * scale).toInt().coerceAtLeast(1),
            (media.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        @Suppress("DEPRECATION") movie.setTime(0)
        @Suppress("DEPRECATION") movie.draw(canvas, 0f, 0f)
        return BackgroundMedia(bitmap, null, bitmap.width, bitmap.height, media.luminance)
    }

    private fun luminance(bitmap: Bitmap): Float {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        var sum = 0f; var count = 0
        for (y in 0 until height step (height / 12).coerceAtLeast(1)) for (x in 0 until width step (width / 12).coerceAtLeast(1)) {
            val color = bitmap.getPixel(x, y)
            sum += (((color shr 16) and 255) * .2126f + ((color shr 8) and 255) * .7152f + (color and 255) * .0722f) / 255f
            count++
        }
        return if (count == 0) .5f else sum / count
    }
}
