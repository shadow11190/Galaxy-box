package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.performance.*
import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private val current = RefreshMode(1, 1440, 3120, 60f)
    private val modes = listOf(current, RefreshMode(2,1440,3120,90f), RefreshMode(3,1440,3120,144f), RefreshMode(4,1080,2340,240f))
    @Test fun choosesDynamicMaximumWithoutChangingResolution() {
        val choice = RefreshPolicy.decide(current,modes,true,false,false)
        assertEquals(144f, choice.hz!!, .001f); assertEquals(3,choice.modeId)
        val faster = modes + RefreshMode(5,1440,3120,165f)
        assertEquals(165f,RefreshPolicy.decide(current,faster,true,false,false).hz!!,.001f)
    }
    @Test fun preservesFractionalHzAndDoesNotAssume120Or60() {
        val mode = RefreshMode(8,1080,1920,59.94f)
        assertEquals(59.94f,RefreshPolicy.decide(mode,listOf(mode),true,false,false).hz!!,.0001f)
        val mode90 = mode.copy(id=9,hz=90f)
        assertEquals(90f,RefreshPolicy.decide(mode,listOf(mode,mode90),true,false,false).hz!!,.001f)
    }
    @Test fun powerThermalAndBackgroundReleaseTheVote() {
        assertNull(RefreshPolicy.decide(current,modes,false,false,false).hz)
        assertNull(RefreshPolicy.decide(current,modes,true,true,false).hz)
        assertNull(RefreshPolicy.decide(current,modes,true,false,true).hz)
    }
    @Test fun invalidModesAndMissingDisplayDoNotInventRefreshRates() {
        val invalid = listOf(current.copy(hz=Float.NaN),current.copy(hz=Float.POSITIVE_INFINITY),current.copy(hz=0f))
        assertEquals(60f,RefreshPolicy.decide(current,invalid,true,false,false).hz!!,.001f)
        assertNull(RefreshPolicy.decide(null,modes,true,false,false).hz)
        assertNull(RefreshPolicy.decide(current.copy(hz=0f),invalid,true,false,false).hz)
    }
}
