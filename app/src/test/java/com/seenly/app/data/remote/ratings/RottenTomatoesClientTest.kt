package com.seenly.app.data.remote.ratings

import com.seenly.app.TestSupport
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RottenTomatoesClientTest {

    private val client = RottenTomatoesClient(OkHttpClient(), TestSupport.dispatchers)

    private val searchHtml = """
        <search-page-media-row release-year="1999" tomatometer-score="92">
          <a href="https://www.rottentomatoes.com/m/the_matrix" data-qa="info-name">The Matrix</a>
        </search-page-media-row>
        <search-page-media-row release-year="2021" tomatometer-score="88">
          <a href="https://www.rottentomatoes.com/tv/the_matrix" data-qa="info-name">The Matrix</a>
        </search-page-media-row>
        <search-page-media-row release-year="2003">
          <a href="https://www.rottentomatoes.com/m/the_matrix_reloaded" data-qa="info-name">The Matrix Reloaded</a>
        </search-page-media-row>
    """.trimIndent()

    @Test
    fun `search results prefer the matching type and year`() {
        val series = client.parseSearchResults(searchHtml, "The Matrix", 2021, isSeries = true)
        assertEquals("https://www.rottentomatoes.com/tv/the_matrix", series?.href)
        assertEquals(88, series?.score)

        val movie = client.parseSearchResults(searchHtml, "The Matrix", 1999, isSeries = false)
        assertEquals("https://www.rottentomatoes.com/m/the_matrix", movie?.href)
        assertEquals(92, movie?.score)
    }

    @Test
    fun `title page JSON-LD supplies a missing score`() {
        val html = """
            <script type="application/ld+json">
            {"@type":"Movie","name":"The Matrix","aggregateRating":{"@type":"AggregateRating","ratingValue":"83","ratingCount":"100"}}
            </script>
        """.trimIndent()
        assertEquals(83, client.parseAggregateRating(html))
        assertNull(client.parseAggregateRating("""{"ratingValue":"120"}"""))
    }

    @Test
    fun `search page without matching rows returns null`() {
        assertNull(client.parseSearchResults(searchHtml, "Something Else Entirely", null, null))
    }
}
