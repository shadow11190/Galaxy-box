package dev.galaxy.toolbox.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class UsagePeriodTest {
    @Test fun periodStartsUseLocalCalendarBoundariesAcrossDaylightSaving() {
        val zone = ZoneId.of("Europe/Bucharest")
        val now = Instant.parse("2026-10-25T14:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-24T21:00:00Z").toEpochMilli(), UsagePeriod.DAY.start(now, zone))
        assertEquals(Instant.parse("2026-10-18T21:00:00Z").toEpochMilli(), UsagePeriod.WEEK.start(now, zone))
        assertEquals(Instant.parse("2026-09-30T21:00:00Z").toEpochMilli(), UsagePeriod.MONTH.start(now, zone))
    }
}
