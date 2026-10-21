package com.seenly.app.data.local.mapper

import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.relation.EpisodeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMappersTest {

    private fun row(watchedAt: Long?) = EpisodeRow(
        episode = EpisodeEntity(
            id = 5,
            mediaId = 1,
            seasonId = 2,
            number = 3,
            absoluteNumber = 7,
            title = "Episode title",
        ),
        watchedAt = watchedAt,
        rewatched = false,
    )

    @Test
    fun `episode row is watched when a watch date exists`() {
        val episode = row(watchedAt = 1_700_000_000_000).toDomain()
        assertTrue(episode.watched)
        assertEquals(1_700_000_000_000, episode.watchedAt)
    }

    @Test
    fun `episode row without a watch date is unwatched`() {
        val episode = row(watchedAt = null).toDomain()
        assertFalse(episode.watched)
        assertEquals(null, episode.watchedAt)
        assertEquals(3, episode.number)
        assertEquals(7, episode.absoluteNumber)
    }
}
