package dev.galaxy.toolbox.data

import dev.galaxy.toolbox.model.FirmwareQuery
import org.junit.Assert.*
import org.junit.Test

class SamsungChangelogParserTest {
    private val query = FirmwareQuery.create("SM-S9280", "TGY")
    @Test fun resolvesOnlySameModelOfficialEnglishPage() {
        val html = "<option value='../../SM-S9280/029245240224/eng.html'>English</option>"
        assertEquals("https://doc.samsungmobile.com/SM-S9280/029245240224/eng.html", SamsungChangelogParser.englishPage(query, html))
        assertThrows(IllegalStateException::class.java) {
            SamsungChangelogParser.englishPage(query, "<option value='https://evil.example/eng.html'>English</option>")
        }
    }
    @Test fun readsFirstEntryWithoutSortingOrMixingDates() {
        val html = """
            <div><strong>Build Number : </strong>Z123</div>
            <div><strong>Release Date : </strong>2025-04-01</div>
            <div><strong>Build Number : </strong>Z999</div>
            <div><strong>Security patch level : </strong>2025-05-01</div>
        """.trimIndent()
        val first = SamsungChangelogParser.firstEntry(html)
        assertEquals("Z123", first.build)
        assertEquals("2025-04-01", first.releaseDate)
        assertNull(first.securityPatch)
    }
    @Test(expected = IllegalStateException::class) fun changedLayoutFailsClearly() {
        SamsungChangelogParser.firstEntry("<h1>Not available</h1>")
    }
}
