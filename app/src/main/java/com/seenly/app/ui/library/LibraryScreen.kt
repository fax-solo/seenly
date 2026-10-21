package com.seenly.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.LibraryFilter
import com.seenly.app.domain.model.LibrarySort
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.MediaCard
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.components.watchStatusLabel
import com.seenly.app.ui.formatRating
import com.seenly.app.ui.seenlyViewModel
import com.seenly.app.ui.formatNumber

@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenMedia: (Long) -> Unit,
    onAddManual: () -> Unit,
    onDiscover: () -> Unit,
    viewModel: LibraryViewModel = seenlyViewModel { LibraryViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    val errorText = appErrorText(state.error)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.allEntries.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.library_count, state.allEntries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAddManual) {
                Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = stringResource(R.string.add_manual_title))
            }
            Box {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.sort))
                }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    LibrarySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sortLabel(sort)) },
                            onClick = {
                                viewModel.setSort(sort)
                                showSort = false
                            },
                            trailingIcon = if (sort == state.sort) {
                                { Icon(Icons.Rounded.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (errorText != null) {
            ErrorBanner(message = errorText)
        }

        OutlinedTextField(
            value = state.filter.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.search_library)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            item {
                FilterChip(
                    selected = state.filter.statuses.isEmpty(),
                    onClick = { viewModel.clearAll() },
                    label = { Text(stringResource(R.string.all)) },
                )
            }
            items(WatchStatus.entries) { status ->
                FilterChip(
                    selected = status in state.filter.statuses,
                    onClick = { viewModel.toggleStatus(status) },
                    label = { Text(watchStatusLabel(status)) },
                )
            }
            item {
                AssistChip(
                    onClick = { showFilters = true },
                    label = {
                        Text(
                            if (activeFilterCount(state.filter) > 0) {
                                stringResource(R.string.filters_count, activeFilterCount(state.filter))
                            } else {
                                stringResource(R.string.filter)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.entries.isEmpty()) {
            if (state.allEntries.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Explore,
                    title = stringResource(R.string.empty_library_title),
                    message = stringResource(R.string.empty_library_message),
                    actionLabel = stringResource(R.string.discover_title),
                    onAction = onDiscover,
                )
            } else {
                EmptyState(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.empty_filter_title),
                    message = stringResource(R.string.empty_filter_message),
                    actionLabel = stringResource(R.string.clear_filters),
                    onAction = viewModel::clearAll,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.entries, key = { it.media.id }) { entry ->
                    MediaCard(
                        entry = entry,
                        onClick = { onOpenMedia(entry.media.id) },
                    )
                }
            }
        }
    }

    if (showFilters) {
        LibraryFilterDialog(
            filter = state.filter,
            genres = state.genres,
            onDismiss = { showFilters = false },
            onApply = { types, genres, rating ->
                viewModel.setTypes(types)
                viewModel.setGenres(genres)
                viewModel.setMinimumRating(rating)
                showFilters = false
            },
            onClear = {
                viewModel.clearFilters()
                showFilters = false
            },
        )
    }
}

@Composable
private fun LibraryFilterDialog(
    filter: LibraryFilter,
    genres: List<String>,
    onDismiss: () -> Unit,
    onApply: (Set<MediaType>, Set<String>, Double?) -> Unit,
    onClear: () -> Unit,
) {
    var selectedTypes by remember { mutableStateOf(filter.types) }
    var selectedGenres by remember { mutableStateOf(filter.genres) }
    var minimumRating by remember { mutableStateOf(filter.minimumRating) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_library)) },
        text = {
            Column(Modifier.heightIn(max = 430.dp)) {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        Text(
                            text = stringResource(R.string.filter_types),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    items(MediaType.entries) { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = type in selectedTypes,
                                onCheckedChange = { checked ->
                                    selectedTypes = if (checked) selectedTypes + type else selectedTypes - type
                                },
                            )
                            Text(mediaTypeLabel(type), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (genres.isNotEmpty()) {
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text(
                                text = stringResource(R.string.filter_genres),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(genres) { genre ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedGenres.any { it.equals(genre, ignoreCase = true) },
                                    onCheckedChange = { checked ->
                                        selectedGenres = if (checked) {
                                            selectedGenres + genre
                                        } else {
                                            selectedGenres.filterNot { it.equals(genre, ignoreCase = true) }.toSet()
                                        }
                                    },
                                )
                                Text(genre, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            text = minimumRating?.let {
                                stringResource(R.string.filter_min_rating, formatRating(it) ?: "")
                            } ?: stringResource(R.string.filter_any_rating),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Slider(
                            value = (minimumRating ?: 0.0).toFloat(),
                            onValueChange = { value ->
                                minimumRating = if (value <= 0f) null else value.toDouble()
                            },
                            valueRange = 0f..10f,
                            steps = 19,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedTypes, selectedGenres, minimumRating) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun sortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.RECENTLY_ADDED -> R.string.sort_recently_added
        LibrarySort.RECENTLY_WATCHED -> R.string.sort_recently_watched
        LibrarySort.TITLE_ASC -> R.string.sort_title_asc
        LibrarySort.TITLE_DESC -> R.string.sort_title_desc
        LibrarySort.RATING_DESC -> R.string.sort_rating_desc
        LibrarySort.RELEASE_DATE_DESC -> R.string.sort_release_desc
    },
)

private fun activeFilterCount(filter: LibraryFilter): Int =
    (if (filter.types.isNotEmpty()) 1 else 0) +
        (if (filter.genres.isNotEmpty()) 1 else 0) +
        (if (filter.minimumRating != null) 1 else 0)
