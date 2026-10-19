package dev.galaxy.toolbox

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.galaxy.toolbox.ui.FilmPattern
import dev.galaxy.toolbox.ui.ScreenWindowSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FilmAssistantTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun filmCanHideControlsAndRecoverWithoutLeavingTheTool() {
        rule.onNodeWithText("檢測").performClick()
        rule.onNodeWithText("開啟貼膜輔助").performClick()
        rule.onNodeWithText("邊緣檢視").performClick()
        rule.onNodeWithText("全螢幕開始").performClick()
        rule.onNodeWithText("全螢幕開始").assertDoesNotExist()
        rule.onNodeWithTag("film-preview").performTouchInput { doubleClick() }
        rule.onNodeWithText("結束").assertIsDisplayed().performClick()
        rule.onNodeWithText("開啟貼膜輔助").assertIsDisplayed()
    }
    @Test fun onePixelBorderIsInsideImageAndWhitePresetHasNoOverlay() {
        val bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
        try {
            FilmPattern(Color.WHITE, Color.BLACK, 1, false, false, "").draw(Canvas(bitmap), 100f, 200f)
            assertEquals(Color.BLACK, bitmap.getPixel(0, 100))
            assertEquals(Color.BLACK, bitmap.getPixel(99, 100))
            assertEquals(Color.BLACK, bitmap.getPixel(50, 199))
            assertEquals(Color.WHITE, bitmap.getPixel(1, 100))
            FilmPattern(Color.WHITE, Color.BLACK, 0, false, false, "").draw(Canvas(bitmap), 100f, 200f)
            assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
            assertEquals(Color.WHITE, bitmap.getPixel(50, 100))
        } finally { bitmap.recycle() }
    }
    @Test fun windowBrightnessAndKeepAwakeAreRestoredIdempotently() {
        rule.runOnUiThread {
            val window = rule.activity.window
            val original = window.attributes.screenBrightness
            val flags = window.attributes.flags
            val session = ScreenWindowSession(window)
            session.setBrightness(.8f); session.resume()
            assertEquals(.8f, window.attributes.screenBrightness, .001f)
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            session.restore(); session.restore()
            assertEquals(original, window.attributes.screenBrightness, .001f)
            assertEquals(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
