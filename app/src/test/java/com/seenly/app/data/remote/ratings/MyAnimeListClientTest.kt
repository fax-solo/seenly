package com.seenly.app.data.remote.ratings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyAnimeListClientTest {

    @Test
    fun `parses the rating value metadata`() {
        val html = """
            <div>
              <span itemprop="ratingValue" class="score-label score-8">8.12</span>
              <sup>1</sup>
            </div>
        """.trimIndent()

        assertEquals(8.12, MyAnimeListClient.parseScore(html) ?: 0.0, 0.0)
    }

    @Test
    fun `ignores missing or out of range scores`() {
        assertNull(MyAnimeListClient.parseScore("<html></html>"))
        assertNull(
            MyAnimeListClient.parseScore(
                """<span itemprop="ratingValue">11.2</span>""",
            ),
        )
    }
}
