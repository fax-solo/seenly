package com.seenly.app.data.backup

import com.seenly.app.core.common.AppJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupValidatorTest {

    private fun validBackup() = BackupFile(
        schemaVersion = 1,
        appVersion = "1.0.0",
        exportedAt = 1234,
        media = listOf(
            BackupMedia(id = 1, type = "MOVIE", title = "A Movie", personalRating = 8.5),
            BackupMedia(id = 2, type = "TV_SERIES", title = "A Series"),
        ),
        providerIds = listOf(
            BackupProviderId(mediaId = 1, provider = "TMDB", externalId = "movie/1"),
        ),
        seasons = listOf(
            BackupSeason(id = 10, mediaId = 2, number = 1),
        ),
        episodes = listOf(
            BackupEpisode(id = 100, mediaId = 2, seasonId = 10, number = 1, title = "Pilot"),
        ),
        progress = listOf(
            BackupProgress(
                mediaId = 2,
                status = "COMPLETED",
                startDate = "2026-01-01",
                completionDate = "2026-01-02",
            ),
        ),
        events = listOf(
            BackupEvent(
                mediaId = 2,
                episodeId = 100,
                eventType = "FIRST_WATCH",
                startedAt = 1,
                completedAt = 2,
                localDate = "2026-01-02",
            ),
        ),
        lists = listOf(
            BackupList(id = 1, name = "Favorites", isBuiltin = true, smartFilter = "FAVORITES"),
        ),
        listItems = listOf(BackupListItem(listId = 1, mediaId = 1, position = 0)),
        goals = listOf(BackupGoal(type = "WEEKLY", metric = "EPISODES", target = 3)),
    )

    @Test
    fun `a well formed backup passes validation and round trips through JSON`() {
        val file = validBackup()
        assertNull(BackupValidator.validate(file))

        val json = AppJson.encodeToString(file)
        val decoded = AppJson.decodeFromString<BackupFile>(json)
        assertEquals(file, decoded)
        assertNull(BackupValidator.validate(decoded))
    }

    @Test
    fun `unsupported schema versions are rejected before import`() {
        assertNotNull(BackupValidator.validate(validBackup().copy(schemaVersion = 99)))
        assertNotNull(BackupValidator.validate(validBackup().copy(schemaVersion = 0)))
    }

    @Test
    fun `duplicate and invalid media rows are rejected`() {
        val file = validBackup()
        assertNotNull(
            BackupValidator.validate(file.copy(media = file.media + file.media.first())),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(media = listOf(file.media.first().copy(title = " ")) + file.media.drop(1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(media = listOf(file.media.first().copy(type = "PODCAST")) + file.media.drop(1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(media = listOf(file.media.first().copy(personalRating = 10.5)) + file.media.drop(1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(media = listOf(file.media.first().copy(personalRating = 7.3)) + file.media.drop(1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(media = listOf(file.media.first().copy(releaseDate = "not-a-date")) + file.media.drop(1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(file.copy(media = emptyList(), listItems = emptyList(), providerIds = emptyList())),
        )
    }

    @Test
    fun `provider links must be unique and point at known media`() {
        val file = validBackup()
        val link = file.providerIds.single()
        assertNotNull(
            BackupValidator.validate(file.copy(providerIds = listOf(link, link.copy(id = 2)))),
        )
        assertNotNull(
            BackupValidator.validate(file.copy(providerIds = listOf(link.copy(mediaId = 99)))),
        )
        assertNotNull(
            BackupValidator.validate(file.copy(providerIds = listOf(link.copy(provider = "MANUAL")))),
        )
        assertNotNull(
            BackupValidator.validate(file.copy(providerIds = listOf(link.copy(externalId = " ")))),
        )
    }

    @Test
    fun `season and episode relationships are validated`() {
        val file = validBackup()
        assertNotNull(
            BackupValidator.validate(
                file.copy(seasons = file.seasons + BackupSeason(id = 11, mediaId = 2, number = 1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(episodes = file.episodes + BackupEpisode(id = 101, mediaId = 2, seasonId = 10, number = 1)),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(
                    episodes = listOf(file.episodes.single().copy(mediaId = 1)),
                ),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(episodes = listOf(file.episodes.single().copy(seasonId = 999))),
            ),
        )
        assertNotNull(
            BackupValidator.validate(
                file.copy(seasons = listOf(file.seasons.single().copy(mediaId = 99))),
            ),
        )
    }

    @Test
    fun `progress and events must reference consistent local data`() {
        val file = validBackup()
        val progress = file.progress.single()
        val event = file.events.single()

        assertNotNull(BackupValidator.validate(file.copy(progress = listOf(progress, progress))))
        assertNotNull(BackupValidator.validate(file.copy(progress = listOf(progress.copy(mediaId = 99)))))
        assertNotNull(BackupValidator.validate(file.copy(progress = listOf(progress.copy(status = "SLEEPING")))))
        assertNotNull(BackupValidator.validate(file.copy(progress = listOf(progress.copy(completionDate = "2026-02-30")))))

        assertNotNull(BackupValidator.validate(file.copy(events = listOf(event.copy(eventType = "STARTED")))))
        assertNotNull(BackupValidator.validate(file.copy(events = listOf(event.copy(episodeId = 999)))))
        assertNotNull(BackupValidator.validate(file.copy(events = listOf(event.copy(mediaId = 1)))))
        assertNotNull(BackupValidator.validate(file.copy(events = listOf(event.copy(localDate = "yesterday")))))
    }

    @Test
    fun `lists, memberships and goals are validated`() {
        val file = validBackup()
        val list = file.lists.single()
        val item = file.listItems.single()
        val goal = file.goals.single()

        assertNotNull(BackupValidator.validate(file.copy(lists = listOf(list, list.copy(name = "Duplicate")))))
        assertNotNull(BackupValidator.validate(file.copy(lists = listOf(list.copy(name = "")))))
        assertNotNull(BackupValidator.validate(file.copy(lists = listOf(list.copy(smartFilter = "RECENT")))))
        assertNotNull(BackupValidator.validate(file.copy(listItems = listOf(item, item))))
        assertNotNull(BackupValidator.validate(file.copy(listItems = listOf(item.copy(mediaId = 99)))))
        assertNotNull(BackupValidator.validate(file.copy(listItems = listOf(item.copy(listId = 99)))))
        assertNotNull(BackupValidator.validate(file.copy(goals = listOf(goal.copy(target = 0)))))
        assertNotNull(BackupValidator.validate(file.copy(goals = listOf(goal.copy(type = "DAILY")))))
        assertNotNull(BackupValidator.validate(file.copy(goals = listOf(goal.copy(metric = "SECONDS")))))
    }
}
