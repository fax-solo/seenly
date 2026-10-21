package com.seenly.app.data.local.mapper

import com.seenly.app.core.time.IsoDates.toIsoOrNull
import com.seenly.app.core.time.IsoDates.toLocalDateOrNull
import com.seenly.app.data.local.entity.EpisodeEntity
import com.seenly.app.data.local.entity.GoalEntity
import com.seenly.app.data.local.entity.MediaEntity
import com.seenly.app.data.local.entity.MediaListEntity
import com.seenly.app.data.local.entity.ProgressEntity
import com.seenly.app.data.local.entity.SeasonEntity
import com.seenly.app.data.local.entity.WatchEventEntity
import com.seenly.app.data.local.relation.ActivityRow
import com.seenly.app.data.local.relation.EpisodeRow
import com.seenly.app.data.local.relation.LibraryRow
import com.seenly.app.data.local.relation.ListRow
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.EventType
import com.seenly.app.domain.model.Goal
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.Progress
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.model.Season
import com.seenly.app.domain.model.SmartList
import com.seenly.app.domain.model.WatchEvent
import com.seenly.app.domain.model.WatchStatus

fun MediaEntity.toDomain(genres: List<String> = emptyList()): Media = Media(
    id = id,
    type = MediaType.fromStorage(type) ?: MediaType.MOVIE,
    title = title,
    originalTitle = originalTitle,
    alternateTitles = alternateTitles,
    synopsis = synopsis,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    releaseDate = releaseDate.toLocalDateOrNull(),
    endDate = endDate.toLocalDateOrNull(),
    runtimeMinutes = runtimeMinutes,
    totalSeasons = totalSeasons,
    totalEpisodes = totalEpisodes,
    studio = studio,
    tags = tags,
    directorNames = directorNames,
    castNames = castNames,
    genres = genres,
    providerScore = providerScore,
    imdbId = imdbId,
    imdbRating = imdbRating,
    rottenTomatoesRating = rottenTomatoesRating,
    malRating = malRating,
    ratingsUpdatedAt = ratingsUpdatedAt,
    metadataSource = ProviderId.fromStorage(metadataSource),
    metadataUpdatedAt = metadataUpdatedAt,
    addedAt = addedAt,
    lastModifiedAt = lastModifiedAt,
    personalRating = personalRating,
    review = review,
    reviewIsSpoiler = reviewIsSpoiler,
    reviewCreatedAt = reviewCreatedAt,
    reviewUpdatedAt = reviewUpdatedAt,
    personalNotes = personalNotes,
)

fun SeasonEntity.toDomain(): Season = Season(
    id = id,
    mediaId = mediaId,
    number = number,
    title = title,
    synopsis = synopsis,
    episodeCount = episodeCount,
    airDate = airDate.toLocalDateOrNull(),
    endDate = endDate.toLocalDateOrNull(),
    posterUrl = posterUrl,
    sortOrder = sortOrder,
)

fun EpisodeEntity.toDomain(watched: Boolean = false, watchedAt: Long? = null, rewatched: Boolean = false): Episode =
    Episode(
        id = id,
        mediaId = mediaId,
        seasonId = seasonId,
        number = number,
        absoluteNumber = absoluteNumber,
        title = title,
        synopsis = synopsis,
        runtimeMinutes = runtimeMinutes,
        airDate = airDate.toLocalDateOrNull(),
        stillUrl = stillUrl,
        watched = watched,
        watchedAt = watchedAt,
        rewatched = rewatched,
    )

fun EpisodeRow.toDomain(): Episode = episode.toDomain(
    watched = watchedAt != null,
    watchedAt = watchedAt,
    rewatched = rewatched,
)

fun ProgressEntity.toDomain(): Progress = Progress(
    mediaId = mediaId,
    status = WatchStatus.fromStorage(status),
    startDate = startDate.toLocalDateOrNull(),
    completionDate = completionDate.toLocalDateOrNull(),
    pausedAt = pausedAt,
    notes = notes,
    updatedAt = updatedAt,
)

fun WatchEventEntity.toDomain(): WatchEvent = WatchEvent(
    id = id,
    mediaId = mediaId,
    episodeId = episodeId,
    eventType = EventType.entries.firstOrNull { it.name == eventType } ?: EventType.FIRST_WATCH,
    startedAt = startedAt,
    completedAt = completedAt,
    localDate = localDate.toLocalDateOrNull() ?: java.time.LocalDate.ofEpochDay(0),
    notes = notes,
)

fun LibraryRow.toDomain(genres: List<String> = emptyList()): LibraryEntry = LibraryEntry(
    media = media.toDomain(genres),
    status = WatchStatus.fromStorage(status),
    episodesWatched = episodesWatched,
    episodesTotal = episodesTotal,
    lastActivityAt = lastActivityAt,
    rewatchCount = rewatchCount,
)

fun ListRow.toDomain(): MediaList = MediaList(
    id = list.id,
    name = list.name,
    description = list.description,
    createdAt = list.createdAt,
    sortOrder = list.sortOrder,
    isBuiltin = list.isBuiltin,
    smartFilter = SmartList.fromStorage(list.smartFilter),
    coverUrl = list.coverUrl,
    itemCount = itemCount,
)

fun GoalEntity.toDomain(): Goal = Goal(
    id = id,
    type = GoalType.entries.firstOrNull { it.name == type } ?: GoalType.WEEKLY,
    metric = GoalMetric.entries.firstOrNull { it.name == metric } ?: GoalMetric.EPISODES,
    target = target,
    active = active,
    createdAt = createdAt,
)

fun ActivityRow.toDomain(): ActivityItem = ActivityItem(
    eventId = eventId,
    mediaId = mediaId,
    mediaTitle = mediaTitle,
    posterUrl = posterUrl,
    mediaType = MediaType.fromStorage(mediaType) ?: MediaType.MOVIE,
    episodeId = episodeId,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    episodeTitle = episodeTitle,
    watchedAtMillis = watchedAtMillis,
    localDate = localDate.toLocalDateOrNull() ?: java.time.LocalDate.ofEpochDay(0),
    eventType = EventType.entries.firstOrNull { it.name == eventType } ?: EventType.FIRST_WATCH,
)

/** Entity builders shared by repositories. */
object EntityMappers {

    fun mediaEntity(
        input: com.seenly.app.domain.model.ManualMediaInput,
        now: Long,
        existing: MediaEntity? = null,
    ): MediaEntity = MediaEntity(
        id = existing?.id ?: 0,
        type = input.type.name,
        title = input.title,
        originalTitle = input.originalTitle ?: existing?.originalTitle,
        alternateTitles = existing?.alternateTitles ?: emptyList(),
        synopsis = input.synopsis ?: existing?.synopsis,
        posterUrl = input.posterUrl ?: existing?.posterUrl,
        backdropUrl = existing?.backdropUrl,
        releaseDate = input.releaseDate?.toIsoOrNull() ?: existing?.releaseDate,
        endDate = existing?.endDate,
        runtimeMinutes = input.runtimeMinutes ?: existing?.runtimeMinutes,
        totalSeasons = input.totalSeasons ?: existing?.totalSeasons,
        totalEpisodes = input.totalEpisodes ?: existing?.totalEpisodes,
        studio = existing?.studio,
        tags = existing?.tags ?: emptyList(),
        directorNames = existing?.directorNames ?: emptyList(),
        castNames = existing?.castNames ?: emptyList(),
        providerScore = existing?.providerScore,
        imdbId = existing?.imdbId,
        imdbRating = existing?.imdbRating,
        rottenTomatoesRating = existing?.rottenTomatoesRating,
        malRating = existing?.malRating,
        ratingsUpdatedAt = existing?.ratingsUpdatedAt,
        metadataSource = existing?.metadataSource ?: ProviderId.MANUAL.name,
        metadataUpdatedAt = existing?.metadataUpdatedAt,
        addedAt = existing?.addedAt ?: now,
        lastModifiedAt = now,
        personalRating = existing?.personalRating,
        review = existing?.review,
        reviewIsSpoiler = existing?.reviewIsSpoiler ?: false,
        reviewCreatedAt = existing?.reviewCreatedAt,
        reviewUpdatedAt = existing?.reviewUpdatedAt,
        personalNotes = existing?.personalNotes,
    )
}
