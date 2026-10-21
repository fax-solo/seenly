package com.seenly.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.core.common.AppError
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.LibraryFilter
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import java.text.Collator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val allEntries: List<LibraryEntry> = emptyList(),
    val entries: List<LibraryEntry> = emptyList(),
    val genres: List<String> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val sort: LibrarySort = LibrarySort.RECENTLY_ADDED,
    val error: AppError? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter())

    val state = combine(
        container.libraryRepository.observeLibrary(),
        container.libraryRepository.observeGenres(),
        container.settingsRepository.settings,
        filter,
    ) { library, genres, settings, activeFilter ->
        LibraryUiState(
            loading = false,
            allEntries = library,
            entries = applyFilter(library, activeFilter, settings.defaultLibrarySort),
            genres = genres,
            filter = activeFilter,
            sort = settings.defaultLibrarySort,
        )
    }.catch { failure ->
        emit(
            LibraryUiState(
                loading = false,
                error = AppError.Unknown(failure),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun toggleStatus(status: WatchStatus) {
        val current = filter.value.statuses
        filter.value = filter.value.copy(
            statuses = if (status in current) current - status else current + status,
        )
    }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch {
            container.settingsRepository.setDefaultLibrarySort(sort)
        }
    }

    fun setTypes(types: Set<MediaType>) {
        filter.value = filter.value.copy(types = types)
    }

    fun setGenres(genres: Set<String>) {
        filter.value = filter.value.copy(genres = genres)
    }

    fun setMinimumRating(rating: Double?) {
        filter.value = filter.value.copy(minimumRating = rating)
    }

    fun clearFilters() {
        filter.value = LibraryFilter(query = filter.value.query)
    }

    fun clearAll() {
        filter.value = LibraryFilter()
    }

    private fun applyFilter(
        library: List<LibraryEntry>,
        filter: LibraryFilter,
        sort: LibrarySort,
    ): List<LibraryEntry> {
        val query = filter.query.trim()
        val collator = Collator.getInstance()
        val titleComparator = Comparator<LibraryEntry> { a, b ->
            collator.compare(a.media.title, b.media.title)
        }
        val filtered = library.filter { entry ->
            val media = entry.media
            val matchesQuery = query.isEmpty() ||
                media.title.contains(query, ignoreCase = true) ||
                media.originalTitle?.contains(query, ignoreCase = true) == true
            val matchesStatus = filter.statuses.isEmpty() || entry.status in filter.statuses
            val matchesType = filter.types.isEmpty() || media.type in filter.types
            val matchesGenre = filter.genres.isEmpty() ||
                media.genres.any { genre -> filter.genres.any { it.equals(genre, ignoreCase = true) } }
            val matchesRating = filter.minimumRating == null ||
                (media.personalRating ?: -1.0) >= filter.minimumRating
            val year = media.releaseDate?.year
            val matchesFrom = filter.releaseYearFrom == null || (year != null && year >= filter.releaseYearFrom)
            val matchesTo = filter.releaseYearTo == null || (year != null && year <= filter.releaseYearTo)
            val matchesList = filter.listId == null
            matchesQuery && matchesStatus && matchesType && matchesGenre &&
                matchesRating && matchesFrom && matchesTo && matchesList
        }
        return when (sort) {
            LibrarySort.RECENTLY_ADDED -> filtered.sortedByDescending { it.media.addedAt }
            LibrarySort.RECENTLY_WATCHED -> filtered.sortedByDescending { it.lastActivityAt ?: 0L }
            LibrarySort.TITLE_ASC -> filtered.sortedWith(titleComparator)
            LibrarySort.TITLE_DESC -> filtered.sortedWith(titleComparator.reversed())
            LibrarySort.RATING_DESC -> filtered.sortedWith(
                compareByDescending<LibraryEntry> { it.media.personalRating ?: -1.0 }
                    .then(titleComparator),
            )
            LibrarySort.RELEASE_DATE_DESC -> filtered.sortedByDescending {
                it.media.releaseDate?.toEpochDay() ?: Long.MIN_VALUE
            }
        }
    }
}
