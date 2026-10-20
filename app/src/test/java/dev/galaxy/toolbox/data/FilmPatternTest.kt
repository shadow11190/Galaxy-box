package dev.galaxy.toolbox.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import dev.galaxy.toolbox.ui.FilmPattern
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilmPatternTest {
    @Test fun roundedInsetBorderStaysInsideTheSafeFrame() {
        val b = Bitmap.createBitmap(160,300,Bitmap.Config.ARGB_8888)
        FilmPattern(Color.WHITE,Color.BLACK,2,false,false,"",8f,8f,60f,40f).draw(Canvas(b),160f,300f)
        assertEquals(Color.WHITE,b.getPixel(8,8)); assertEquals(Color.WHITE,b.getPixel(0,150))
        assertEquals(Color.BLACK,b.getPixel(80,8)); assertEquals(Color.BLACK,b.getPixel(8,150))
        assertEquals(Color.WHITE,b.getPixel(8,291)); assertEquals(Color.WHITE,b.getPixel(80,150))
    }
    @Test fun squareOnePixelAndDustModeKeepTheirExistingBehavior() {
        val b = Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888)
        FilmPattern(Color.WHITE,Color.BLACK,1,false,false,"").draw(Canvas(b),100f,200f)
        assertEquals(Color.BLACK,b.getPixel(0,0)); assertEquals(Color.WHITE,b.getPixel(1,1))
        FilmPattern(Color.WHITE,Color.BLACK,0,false,false,"",12f,12f,80f,80f).draw(Canvas(b),100f,200f)
        val pixels=IntArray(20_000); b.getPixels(pixels,0,100,0,0,100,200)
        assertTrue(pixels.all { it==Color.WHITE })
    }
    @Test fun oversizedRadiiAndInsetsAreClampedForSmallWindowsAndExport() {
        val b=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888)
        FilmPattern(Color.WHITE,Color.BLACK,24,true,true,"test",10000f,10000f,Float.POSITIVE_INFINITY,9000f).draw(Canvas(b),20f,30f)
        assertEquals(Color.WHITE,b.getPixel(0,0))
    }
}
