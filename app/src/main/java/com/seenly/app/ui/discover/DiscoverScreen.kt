package com.seenly.app.ui.discover

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.model.ProviderId
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.ProviderPosterCard
import com.seenly.app.ui.components.SectionHeader
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatRating
import com.seenly.app.ui.seenlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    container: AppContainer,
    onOpenTitle: (Long) -> Unit,
    viewModel: DiscoverViewModel = seenlyViewModel { DiscoverViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)

    LaunchedEffect(state.importedMediaId) {
        val id = state.importedMediaId
        if (id != null) {
            viewModel.consumeImported()
            viewModel.closeDetails()
            onOpenTitle(id)
        }
    }

    val showSeasonal = !state.isSearch &&
        state.category == null &&
        state.seasonal.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.discover_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.discover_refresh),
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange(""); viewModel.search() }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.category == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text(stringResource(R.string.all)) },
                )
            }
            lazyItems(MediaCategory.entries) { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text(categoryLabel(category)) },
                )
            }
        }

        if (errorText != null) {
            ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
        }

        if (state.loading && state.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.results.isEmpty() && !showSeasonal) {
            EmptyState(
                icon = Icons.Rounded.Explore,
                title = stringResource(R.string.discover_empty_title),
                message = stringResource(R.string.discover_empty_message),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showSeasonal) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = stringResource(R.string.discover_this_season))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            lazyItems(state.seasonal, key = { "season_${it.externalId}" }) { item ->
                                ProviderPosterCard(
                                    item = item,
                                    onClick = { viewModel.openDetails(item) },
                                    onAdd = { viewModel.openDetails(item) },
                                    added = state.selectedLocalId != null &&
                                        state.selected?.let { it.externalId == item.externalId } == true,
                                    modifier = Modifier.width(124.dp),
                                )
                            }
                        }
                    }
                }
                if (state.results.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = if (state.isSearch) {
                                stringResource(R.string.discover_results)
                            } else {
                                stringResource(R.string.discover_trending)
                            },
                        )
                    }
                    gridItems(state.results, key = { "${it.provider.name}_${it.externalId}" }) { item ->
                    ProviderPosterCard(
                        item = item,
                        onClick = { viewModel.openDetails(item) },
                        onAdd = { viewModel.openDetails(item) },
                        added = state.selectedLocalId != null &&
                            state.selected?.let {
                                it.provider == item.provider && it.externalId == item.externalId
                            } == true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    } else if (state.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 8.dp),
                            ) {
                                Text(stringResource(R.string.discover_load_more))
                            }
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { selected ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeDetails,
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ProviderDetailsContent(
                item = selected,
                state = state,
                onImport = viewModel::requestImport,
                onOpenExisting = { id -> onOpenTitle(id) },
                onDismissError = viewModel::dismissImportError,
            )
        }
    }

    if (state.linkCandidate != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLinkDialog,
            title = { Text(stringResource(R.string.discover_link_title)) },
            text = { Text(stringResource(R.string.discover_link_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmLink(true) }) {
                    Text(stringResource(R.string.discover_link_action))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.confirmLink(false) }) {
                        Text(stringResource(R.string.discover_add_as_new))
                    }
                    TextButton(onClick = viewModel::dismissLinkDialog) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun ProviderDetailsContent(
    item: ProviderMedia,
    state: DiscoverUiState,
    onImport: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    onDismissError: () -> Unit,
) {
    val importError = appErrorText(state.importError)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row {
            PosterImage(
                url = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .width(110.dp)
                    .height(160.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                item.originalTitle?.takeIf { it != item.title }?.let { original ->
                    Text(
                        text = original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = mediaTypeLabel(item.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.releaseDate?.let { date ->
                    Text(
                        text = date.formatMedium(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.score?.let { score ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatRating(score) ?: "",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }

        if (state.selectedLoading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        val details = state.selectedDetails
        if (details != null) {
            if (details.media.genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = details.media.genres.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val ratingSource = details.media
            val ratings = buildList {
                ratingSource.imdbRating?.let {
                    add(stringResource(R.string.rating_imdb) + " " + formatRating(it))
                }
                ratingSource.rottenTomatoesRating?.let {
                    add(stringResource(R.string.rating_rotten_tomatoes) + " $it%")
                }
                ratingSource.malRating?.let {
                    add(stringResource(R.string.rating_myanimelist) + " " + formatRating(it))
                }
            }
            if (ratings.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ratings.forEach { rating ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = rating,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            val episodeCount = details.seasons.sumOf { it.episodes.size }
            if (episodeCount > 0) {
                Text(
                    text = stringResource(R.string.discover_episodes_count, episodeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        (details?.media?.synopsis ?: item.synopsis)?.takeIf { it.isNotBlank() }?.let { synopsis ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.synopsis),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = synopsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (details != null && details.seasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.seasons),
                style = MaterialTheme.typography.titleSmall,
            )
            details.seasons.take(20).forEach { bundle ->
                Text(
                    text = if (bundle.season.number != null) {
                        stringResource(R.string.season_number, bundle.season.number)
                    } else {
                        stringResource(R.string.specials)
                    } + " · " + stringResource(R.string.episode_count, bundle.episodes.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (importError != null) {
            Spacer(Modifier.height(8.dp))
            ErrorBanner(message = importError, onDismiss = onDismissError)
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.provider.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.selectedLocalId != null) {
                    Button(onClick = { onOpenExisting(state.selectedLocalId) }) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.discover_open_in_library))
                    }
                } else {
                    Button(onClick = onImport, enabled = !state.importInProgress) {
                        if (state.importInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.add_to_library))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.discover_data_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun categoryLabel(category: MediaCategory): String = stringResource(
    when (category) {
        MediaCategory.ANIME -> R.string.category_anime
        MediaCategory.MOVIES -> R.string.category_movies
        MediaCategory.SERIES -> R.string.category_series
        MediaCategory.CARTOONS -> R.string.category_cartoons
    },
)

private fun ProviderId.displayName(): String = when (this) {
    ProviderId.ANILIST -> "AniList"
    ProviderId.CINEMETA -> "Cinemeta (IMDb)"
    ProviderId.TVMAZE -> "TVmaze"
    ProviderId.JIKAN -> "Jikan"
    ProviderId.MANUAL -> "Manual"
}
