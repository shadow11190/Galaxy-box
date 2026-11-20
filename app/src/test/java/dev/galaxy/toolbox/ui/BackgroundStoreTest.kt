package dev.galaxy.toolbox.ui

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundStoreTest {
    @Test fun presetsSurviveReloadAndDeletingActivePresetDisablesCustomBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preset = BackgroundPreset("test-background", "測試", "test-background.png", "png",
            fill = BackgroundFill.CONTAIN, aspect = BackgroundAspect.WIDE,
            zoom = 1.7f, panX = .2f, blur = 4f, brightness = .8f, overlay = .4f)
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            BackgroundStore.file(context, preset).outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        BackgroundStore.save(context, CustomBackgroundState(true, preset.id, listOf(preset)))
        val loaded = BackgroundStore.load(context)
        assertEquals(preset, loaded.active)
        assertTrue(loaded.enabled)
        val removed = BackgroundStore.delete(context, loaded, preset.id)
        assertFalse(removed.enabled)
        assertNull(removed.active)
        assertFalse(BackgroundStore.file(context, preset).exists())
        BackgroundStore.save(context, removed)
    }

    @Test fun freeCropKeepsOriginalRatioWhileScreenCropUsesDisplayRatio() {
        val media = BackgroundMedia(null, null, 1200, 800, .5f)
        val free = sourceCrop(media, BackgroundPreset("free", "", "", "png", aspect = BackgroundAspect.FREE), 9f / 16f)
        val screen = sourceCrop(media, BackgroundPreset("screen", "", "", "png", aspect = BackgroundAspect.SCREEN), 9f / 16f)
        assertEquals(1.5f, free.width() / free.height(), .0001f)
        assertEquals(9f / 16f, screen.width() / screen.height(), .0001f)
    }
}
