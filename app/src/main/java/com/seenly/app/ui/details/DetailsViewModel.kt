package com.seenly.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.core.common.Outcome
import com.seenly.app.domain.model.CompletionNotice
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.ProgressSummary
import com.seenly.app.domain.model.Season
import com.seenly.app.domain.model.WatchEvent
import com.seenly.app.domain.model.WatchStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsUiState(
    val loading: Boolean = true,
    val media: Media? = null,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val summary: ProgressSummary? = null,
    val history: List<WatchEvent> = emptyList(),
    val lists: List<MediaList> = emptyList(),
    val memberships: Set<Long> = emptySet(),
    val favorite: Boolean = false,
    val busy: Boolean = false,
    val deleted: Boolean = false,
    val error: AppError? = null,
    /** Set when this title transitions into COMPLETED; shown once as a snackbar. */
    val completionNotice: CompletionNotice? = null,
)

private data class CoreData(
    val media: Media?,
    val seasons: List<Season>,
    val episodes: List<Episode>,
    val summary: ProgressSummary?,
)

private data class PersonalData(
    val history: List<WatchEvent>,
    val lists: List<MediaList>,
    val memberships: Set<Long>,
    val favorite: Boolean,
)

class DetailsViewModel(
    private val container: AppContainer,
    private val mediaId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsUiState())
    val state = _state.asStateFlow()

    init {
        val core = combine(
            container.libraryRepository.observeMedia(mediaId),
            container.libraryRepository.observeSeasons(mediaId),
            container.libraryRepository.observeEpisodes(mediaId),
            container.watchRepository.observeProgressSummary(mediaId),
        ) { media, seasons, episodes, summary ->
            CoreData(media, seasons, episodes, summary)
        }

        val favorites = container.listsRepository.observeFavorites()
            .map { entries -> entries.any { it.media.id == mediaId } }

        val personal = combine(
            container.watchRepository.observeHistory(mediaId),
            container.listsRepository.observeLists(),
            container.listsRepository.observeMembership(mediaId),
            favorites,
        ) { history, lists, memberships, favorite ->
            PersonalData(history, lists, memberships, favorite)
        }

        viewModelScope.launch {
            combine(core, personal) { coreData, personalData ->
                DetailsUiState(
                    loading = false,
                    media = coreData.media,
                    seasons = coreData.seasons,
                    episodes = coreData.episodes,
                    summary = coreData.summary,
                    history = personalData.history,
                    lists = personalData.lists,
                    memberships = personalData.memberships,
                    favorite = personalData.favorite,
                )
            }.catch { failure ->
                _state.update { it.copy(loading = false, error = AppError.Unknown(failure)) }
            }.collect { newState ->
                _state.update { current ->
                    newState.copy(
                        busy = current.busy,
                        deleted = current.deleted,
                        error = current.error,
                        completionNotice = current.completionNotice,
                    )
                }
            }
        }

        viewModelScope.launch {
            container.watchRepository.completions.collect { notice ->
                if (notice.mediaId == mediaId) {
                    _state.update { it.copy(completionNotice = notice) }
                }
            }
        }

        // Older imports were created before franchise traversal understood OVA
        // bridges, so they can be missing later seasons. Refresh stale anime
        // metadata quietly in the background; the season selector updates when
        // the new rows arrive.
        viewModelScope.launch {
            val media = container.libraryRepository.getMedia(mediaId) ?: return@launch
            if (media.type.category != MediaCategory.ANIME) return@launch
            val lastUpdated = media.metadataUpdatedAt ?: 0L
            val stale = container.clock.nowMillis() - lastUpdated >= AUTO_REFRESH_AGE_MS
            if (stale) {
                container.catalogRepository.refresh(mediaId)
            }
        }
    }

    fun setStatus(status: WatchStatus) = act { container.watchRepository.setStatus(mediaId, status) }

    fun markMovieWatched() = act { container.watchRepository.markMovieWatched(mediaId) }

    fun toggleEpisode(episode: Episode) = act {
        if (episode.watched) {
            container.watchRepository.markEpisodeUnwatched(episode.id)
        } else {
            container.watchRepository.markEpisodeWatched(episode.id)
        }
    }

    fun markSeasonWatched(seasonId: Long) =
        act { container.watchRepository.markSeasonWatched(seasonId) }

    fun markPreviousEpisodes(episodeId: Long) =
        act { container.watchRepository.markPreviousEpisodesWatched(episodeId) }

    fun setCurrentEpisode(episodeId: Long) =
        act { container.watchRepository.setCurrentEpisode(mediaId, episodeId) }

    fun recordRewatch(episodeId: Long?) =
        act { container.watchRepository.recordRewatch(mediaId, episodeId) }

    fun setRewatchCount(count: Int) =
        act { container.watchRepository.setRewatchCount(mediaId, count) }

    fun resetProgress() = act { container.watchRepository.resetProgress(mediaId) }

    fun updateEventDate(eventId: Long, date: LocalDate) =
        act { container.watchRepository.updateEventDate(eventId, date) }

    fun deleteEvent(eventId: Long) =
        act { container.watchRepository.deleteEvent(eventId) }

    fun savePersonalInfo(
        rating: Double?,
        review: String?,
        isSpoiler: Boolean,
        notes: String?,
    ) = act {
        container.libraryRepository.updatePersonalInfo(
            mediaId = mediaId,
            rating = rating,
            review = review,
            isSpoiler = isSpoiler,
            notes = notes,
        )
    }

    fun updateLocalInfo(
        title: String,
        type: MediaType,
        totalSeasons: Int?,
        totalEpisodes: Int?,
        runtimeMinutes: Int?,
    ) = act {
        container.libraryRepository.updateLocalInfo(
            mediaId = mediaId,
            title = title,
            type = type,
            totalSeasons = totalSeasons,
            totalEpisodes = totalEpisodes,
            runtimeMinutes = runtimeMinutes,
        )
    }

    fun refreshMetadata() = act { container.catalogRepository.refresh(mediaId) }

    fun toggleFavorite() = act { container.listsRepository.toggleFavorite(mediaId) }

    fun toggleListMembership(listId: Long, isMember: Boolean) = act {
        if (isMember) {
            container.listsRepository.removeFromList(listId, mediaId)
            Outcome.Success(Unit)
        } else {
            container.listsRepository.addToList(listId, mediaId)
        }
    }

    fun deleteTitle() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                container.libraryRepository.deleteMedia(mediaId)
                _state.update { it.copy(busy = false, deleted = true) }
            } catch (failure: Exception) {
                _state.update { it.copy(busy = false, error = AppError.Unknown(failure)) }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissCompletion() {
        _state.update { it.copy(completionNotice = null) }
    }

    private fun act(block: suspend () -> Outcome<*>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val result = block()) {
                is Outcome.Success -> _state.update { it.copy(busy = false) }
                is Outcome.Failure -> _state.update {
                    it.copy(busy = false, error = result.error)
                }
            }
        }
    }

    private companion object {
        const val AUTO_REFRESH_AGE_MS = 12L * 60 * 60 * 1000
    }
}
