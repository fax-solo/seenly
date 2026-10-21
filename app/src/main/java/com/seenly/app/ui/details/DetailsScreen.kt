package com.seenly.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.core.common.AppError
import com.seenly.app.domain.model.Episode
import com.seenly.app.domain.model.Media
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.Season
import com.seenly.app.domain.model.WatchEvent
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.ConfirmDialog
import com.seenly.app.ui.components.DatePickerModal
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.RatingStars
import com.seenly.app.ui.components.StatusPill
import com.seenly.app.ui.components.SurfaceCard
import com.seenly.app.ui.components.eventTypeLabel
import com.seenly.app.ui.components.mediaListName
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.components.watchStatusLabel
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatRating
import com.seenly.app.ui.seenlyViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    container: AppContainer,
    mediaId: Long,
    onBack: () -> Unit,
    viewModel: DetailsViewModel = seenlyViewModel(key = "details_$mediaId") {
        DetailsViewModel(container, mediaId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)
    val snackbarHostState = remember { SnackbarHostState() }
    val completionMessage = state.completionNotice?.let { notice ->
        stringResource(R.string.notification_completion_body, notice.title)
    }

    LaunchedEffect(completionMessage) {
        if (completionMessage != null) {
            snackbarHostState.showSnackbar(completionMessage)
            viewModel.dismissCompletion()
        }
    }

    var showReview by rememberSaveable { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showLists by rememberSaveable { mutableStateOf(false) }
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    var showStatusMenu by rememberSaveable { mutableStateOf(false) }
    var showSeasonMenu by rememberSaveable { mutableStateOf(false) }
    var showRewatchDialog by rememberSaveable { mutableStateOf(false) }
    var revealSpoiler by rememberSaveable { mutableStateOf(false) }
    var expandedSeasonId by rememberSaveable(mediaId) { mutableStateOf<Long?>(null) }
    var editingEvent by remember { mutableStateOf<WatchEvent?>(null) }
    var deletingEvent by remember { mutableStateOf<WatchEvent?>(null) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.summary?.nextEpisode?.seasonId, state.seasons) {
        val selectedIsMissing = state.seasons.none { it.id == expandedSeasonId }
        if (expandedSeasonId == null || selectedIsMissing) {
            val nextSeasonId = state.summary?.nextEpisode?.seasonId
                ?.takeIf { id -> state.seasons.any { it.id == id } }
            expandedSeasonId = nextSeasonId ?: state.seasons.firstOrNull()?.id
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val media = state.media
    if (media == null) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = stringResource(R.string.error_title_not_found),
                message = stringResource(R.string.details_not_found_message),
            )
        }
        return
    }

    val summary = state.summary
    val status = summary?.status ?: WatchStatus.PLAN_TO_WATCH
    val isMovie = !media.type.hasEpisodes
    val episodeById = state.episodes.associateBy { it.id }
    val seasonById = state.seasons.associateBy { it.id }
    val selectedSeason = state.seasons.firstOrNull { it.id == expandedSeasonId }
        ?: state.seasons.firstOrNull()
    val selectedSeasonEpisodes = selectedSeason
        ?.let { season -> state.episodes.filter { it.seasonId == season.id } }
        .orEmpty()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (state.favorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = stringResource(R.string.list_favorites),
                        tint = if (state.favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (media.metadataSource != null &&
                    media.metadataSource.name != "MANUAL"
                ) {
                    IconButton(onClick = viewModel::refreshMetadata) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.details_refresh),
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_edit_title)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                showEdit = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_add_to_list)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                showLists = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_reset_progress)) },
                            leadingIcon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                            onClick = {
                                showOverflow = false
                                showReset = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showOverflow = false
                                showDelete = true
                            },
                        )
                    }
                }
            },
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    PosterImage(
                        url = media.backdropUrl ?: media.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                ),
                            ),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    PosterImage(
                        url = media.posterUrl,
                        contentDescription = media.title,
                        modifier = Modifier
                            .width(104.dp)
                            .height(156.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = media.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        media.originalTitle?.takeIf { it != media.title }?.let { original ->
                            Text(
                                text = original,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showReview = true }
                                .padding(vertical = 2.dp),
                        ) {
                            RatingStars(media.personalRating)
                            if (media.personalRating == null) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.details_rate),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val meta = buildList {
                            add(mediaTypeLabel(media.type))
                            media.releaseDate?.let { add(it.year.toString()) }
                            media.runtimeMinutes?.let { add(stringResource(R.string.minutes_short, it)) }
                            if (media.totalSeasons != null) {
                                add(stringResource(R.string.seasons_count, media.totalSeasons))
                            }
                        }
                        Text(
                            text = meta.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        media.providerScore?.let { score ->
                            Text(
                                text = stringResource(
                                    R.string.details_provider_score,
                                    formatRating(score) ?: "",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        StatusPill(status)
                    }
                }
            }

            item {
                ExternalRatingsRow(media)
            }

            if (errorText != null) {
                item {
                    ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showStatusMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(watchStatusLabel(status), modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showStatusMenu,
                            onDismissRequest = { showStatusMenu = false },
                        ) {
                            WatchStatus.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(watchStatusLabel(option)) },
                                    onClick = {
                                        showStatusMenu = false
                                        viewModel.setStatus(option)
                                    },
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { showLists = true }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lists_title))
                    }
                }
            }

            if (!isMovie && status != WatchStatus.COMPLETED) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        val markingComplete = status == WatchStatus.WATCHING
                        FilledTonalButton(
                            onClick = {
                                viewModel.setStatus(
                                    if (markingComplete) WatchStatus.COMPLETED else WatchStatus.WATCHING,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (markingComplete) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    when {
                                        markingComplete -> R.string.details_mark_completed
                                        status == WatchStatus.PLAN_TO_WATCH -> R.string.details_start_watching
                                        else -> R.string.details_resume_watching
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            if (isMovie) {
                item {
                    MovieActionCard(
                        watched = status == WatchStatus.COMPLETED,
                        onMarkWatched = viewModel::markMovieWatched,
                        onRewatch = { viewModel.recordRewatch(null) },
                    )
                }
            } else {
                item {
                    ProgressCard(
                        summary = summary,
                        media = media,
                        onMarkNext = {
                            summary?.nextEpisode?.let { episode ->
                                viewModel.toggleEpisode(episode)
                            }
                        },
                    )
                }
            }

            item {
                RewatchCard(
                    count = summary?.rewatchCount ?: 0,
                    onEdit = { showRewatchDialog = true },
                )
            }

            if (media.genres.isNotEmpty()) {
                item {
                    Text(
                        text = media.genres.joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            media.synopsis?.takeIf { it.isNotBlank() }?.let { synopsis ->
                item {
                    SynopsisSection(synopsis = synopsis)
                }
            }

            if (isMovie) {
                item {
                    MovieHistorySection(
                        events = state.history,
                        onEditDate = { editingEvent = it },
                        onDelete = { deletingEvent = it },
                    )
                }
            } else if (state.episodes.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = stringResource(R.string.details_episodes),
                        trailing = stringResource(
                            R.string.episodes_watched_of,
                            summary?.episodesWatched ?: 0,
                            summary?.episodesTotal ?: state.episodes.size,
                        ),
                    )
                }
                if (state.seasons.isEmpty()) {
                    items(state.episodes.size) { index ->
                        val episode = state.episodes[index]
                        EpisodeRow(
                            episode = episode,
                            onToggle = { viewModel.toggleEpisode(episode) },
                            onRewatch = { viewModel.recordRewatch(episode.id) },
                            onSetCurrent = { viewModel.setCurrentEpisode(episode.id) },
                        )
                    }
                } else {
                    item(key = "season_selector") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showSeasonMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = selectedSeason?.let { seasonLabel(it) }
                                        ?: stringResource(R.string.details_select_season),
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showSeasonMenu,
                                onDismissRequest = { showSeasonMenu = false },
                            ) {
                                state.seasons.forEach { season ->
                                    DropdownMenuItem(
                                        text = { Text(seasonLabel(season)) },
                                        onClick = {
                                            expandedSeasonId = season.id
                                            showSeasonMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (selectedSeason != null) {
                        item(key = "season_${selectedSeason.id}") {
                            SeasonHeader(
                                season = selectedSeason,
                                episodes = selectedSeasonEpisodes,
                                expanded = true,
                                onToggle = { showSeasonMenu = true },
                                onMarkSeason = { viewModel.markSeasonWatched(selectedSeason.id) },
                            )
                        }
                        if (selectedSeasonEpisodes.isEmpty()) {
                            item(key = "season_empty_${selectedSeason.id}") {
                                Text(
                                    text = stringResource(R.string.details_season_not_aired),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(
                            count = selectedSeasonEpisodes.size,
                            key = { index -> "episode_${selectedSeasonEpisodes[index].id}" },
                        ) { index ->
                            val episode = selectedSeasonEpisodes[index]
                            EpisodeRow(
                                episode = episode,
                                onToggle = { viewModel.toggleEpisode(episode) },
                                onRewatch = { viewModel.recordRewatch(episode.id) },
                                onSetCurrent = { viewModel.setCurrentEpisode(episode.id) },
                            )
                        }
                    }
                }
                item {
                    SeriesHistorySection(
                        events = state.history,
                        episodeById = episodeById,
                        seasonById = seasonById,
                        onEditDate = { editingEvent = it },
                        onDelete = { deletingEvent = it },
                    )
                }
            } else {
                item {
                    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column {
                            Text(
                                text = stringResource(R.string.details_no_episodes_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.details_no_episodes_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = viewModel::refreshMetadata) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.details_refresh))
                            }
                        }
                    }
                }
            }

            item {
                ReviewSection(
                    media = media,
                    revealSpoiler = revealSpoiler,
                    onToggleSpoiler = { revealSpoiler = !revealSpoiler },
                    onEdit = { showReview = true },
                )
            }

            summary?.let { progress ->
                if (progress.startDate != null || progress.completionDate != null) {
                    item {
                        SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Column {
                                progress.startDate?.let { date ->
                                    Row {
                                        Text(
                                            text = stringResource(R.string.details_started),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(date.formatMedium(), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                progress.completionDate?.let { date ->
                                    Row {
                                        Text(
                                            text = stringResource(R.string.details_completed),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(date.formatMedium(), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                val source = media.metadataSource?.name
                    ?.replaceFirstChar { it.uppercase() }
                    ?: stringResource(R.string.manual)
                SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        Text(
                            text = stringResource(R.string.details_metadata_source, source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.details_metadata_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.details_provider_attribution),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
    )
    }

    if (showReview) {
        ReviewDialog(
            media = media,
            onDismiss = { showReview = false },
            onSave = { rating, review, spoiler, notes ->
                viewModel.savePersonalInfo(rating, review, spoiler, notes)
                showReview = false
            },
        )
    }

    if (showEdit) {
        EditTitleDialog(
            media = media,
            onDismiss = { showEdit = false },
            onSave = { title, type, seasons, episodes, runtime ->
                viewModel.updateLocalInfo(title, type, seasons, episodes, runtime)
                showEdit = false
            },
        )
    }

    if (showLists) {
        ListPickerDialog(
            lists = state.lists.filter { it.smartFilter?.name != "PLAN_TO_WATCH" },
            memberships = state.memberships,
            onToggle = viewModel::toggleListMembership,
            onDismiss = { showLists = false },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.details_delete_title),
            text = stringResource(R.string.details_delete_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                showDelete = false
                viewModel.deleteTitle()
            },
            onDismiss = { showDelete = false },
        )
    }

    if (showReset) {
        ConfirmDialog(
            title = stringResource(R.string.details_reset_title),
            text = stringResource(R.string.details_reset_message),
            confirmLabel = stringResource(R.string.details_reset_progress),
            destructive = true,
            onConfirm = {
                showReset = false
                viewModel.resetProgress()
            },
            onDismiss = { showReset = false },
        )
    }

    if (showRewatchDialog) {
        RewatchDialog(
            currentCount = summary?.rewatchCount ?: 0,
            onDismiss = { showRewatchDialog = false },
            onSave = { count ->
                showRewatchDialog = false
                viewModel.setRewatchCount(count)
            },
        )
    }

    editingEvent?.let { event ->
        DatePickerModal(
            initialDate = event.localDate,
            onConfirm = { date ->
                viewModel.updateEventDate(event.id, date)
                editingEvent = null
            },
            onDismiss = { editingEvent = null },
        )
    }

    deletingEvent?.let { event ->
        ConfirmDialog(
            title = stringResource(R.string.details_delete_event_title),
            text = stringResource(R.string.details_delete_event_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteEvent(event.id)
                deletingEvent = null
            },
            onDismiss = { deletingEvent = null },
        )
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExternalRatingsRow(media: Media) {
    data class Badge(val label: String, val value: String)

    val badges = buildList {
        media.imdbRating?.let { score ->
            add(Badge(stringResource(R.string.rating_imdb), formatRating(score).orEmpty()))
        }
        media.rottenTomatoesRating?.let { score ->
            add(Badge(stringResource(R.string.rating_rotten_tomatoes), "$score%"))
        }
        media.malRating?.let { score ->
            add(Badge(stringResource(R.string.rating_myanimelist), formatRating(score).orEmpty()))
        }
    }
    if (badges.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        badges.forEach { badge ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = badge.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = badge.value, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    if (media.ratingsUpdatedAt != null) {
        Text(
            text = stringResource(R.string.rating_source_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MovieActionCard(
    watched: Boolean,
    onMarkWatched: () -> Unit,
    onRewatch: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column {
            Text(
                text = stringResource(R.string.details_movie),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (watched) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onMarkWatched) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_mark_watched))
                    }
                    OutlinedButton(onClick = onRewatch) {
                        Icon(Icons.Rounded.Replay, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.details_watch_again))
                    }
                }
            } else {
                Button(onClick = onMarkWatched) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.details_mark_watched))
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    summary: com.seenly.app.domain.model.ProgressSummary?,
    media: Media,
    onMarkNext: () -> Unit,
) {
    val watched = summary?.episodesWatched ?: 0
    val total = summary?.episodesTotal ?: 0
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.details_progress),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (total > 0) "$watched / $total" else stringResource(R.string.details_unknown_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) watched.toFloat() / total else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            summary?.nextEpisode?.let { next ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.details_next_episode, next.number ?: 0),
                    style = MaterialTheme.typography.titleSmall,
                )
                next.title?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = onMarkNext) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.details_mark_episode_watched))
                }
            }
            if (summary?.nextEpisode == null && total > 0 && watched >= total) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.details_all_episodes_watched),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (media.totalEpisodes == null && total == 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.details_no_episode_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RewatchCard(
    count: Int,
    onEdit: () -> Unit,
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.details_rewatch_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.details_rewatch_count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Rounded.Replay, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.details_set_rewatch_count))
            }
        }
    }
}

@Composable
private fun RewatchDialog(
    currentCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var input by rememberSaveable(currentCount) { mutableStateOf(currentCount.toString()) }
    val parsed = input.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_rewatch_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.details_rewatch_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all(Char::isDigit)) input = value
                    },
                    label = { Text(stringResource(R.string.details_rewatch_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed in 0..100,
                onClick = { parsed?.let(onSave) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun seasonLabel(season: Season): String =
    if (season.number != null) {
        stringResource(R.string.season_number, season.number)
    } else {
        stringResource(R.string.specials)
    }

@Composable
private fun SeasonHeader(
    season: Season,
    episodes: List<Episode>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMarkSeason: () -> Unit,
) {
    val watched = episodes.count { it.watched }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = seasonLabel(season),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.details_season_progress, watched, episodes.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onMarkSeason,
                enabled = episodes.isNotEmpty(),
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.details_mark_season),
                    tint = when {
                        episodes.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        watched == episodes.size -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    onToggle: () -> Unit,
    onRewatch: () -> Unit,
    onSetCurrent: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = episode.watched, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            val episodeTitle = episode.title?.takeIf { it.isNotBlank() }
            Text(
                text = when {
                    episodeTitle != null && episode.number != null ->
                        "${stringResource(R.string.episode_short, episode.number)} · $episodeTitle"

                    episodeTitle != null -> episodeTitle
                    episode.number != null -> stringResource(R.string.episode_number, episode.number)
                    else -> stringResource(R.string.untitled_episode)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                episode.airDate?.let { add(it.formatMedium()) }
                episode.runtimeMinutes?.let { add(stringResource(R.string.minutes_short, it)) }
            }
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (episode.watched) {
            IconButton(onClick = onRewatch) {
                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = stringResource(R.string.details_watch_again),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onSetCurrent) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.details_watched_up_to),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SeriesHistorySection(
    events: List<WatchEvent>,
    episodeById: Map<Long, Episode>,
    seasonById: Map<Long, Season>,
    onEditDate: (WatchEvent) -> Unit,
    onDelete: (WatchEvent) -> Unit,
) {
    if (events.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SectionTitle(
        title = stringResource(R.string.details_history),
        trailing = stringResource(R.string.details_events_count, events.size),
    )
    events.take(50).forEach { event ->
        val episode = event.episodeId?.let { episodeById[it] }
        val season = episode?.let { seasonById[it.seasonId] }
        HistoryRow(
            event = event,
            label = if (episode != null) {
                buildString {
                    if (season?.number != null) {
                        append(stringResource(R.string.season_short, season.number))
                        append(" · ")
                    }
                    append(stringResource(R.string.episode_short, episode.number ?: 0))
                    episode.title?.let {
                        append(" · ")
                        append(it)
                    }
                }
            } else {
                stringResource(R.string.details_full_rewatch)
            },
            onEditDate = { onEditDate(event) },
            onDelete = { onDelete(event) },
        )
    }
}

@Composable
private fun MovieHistorySection(
    events: List<WatchEvent>,
    onEditDate: (WatchEvent) -> Unit,
    onDelete: (WatchEvent) -> Unit,
) {
    if (events.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SectionTitle(
        title = stringResource(R.string.details_history),
        trailing = stringResource(R.string.details_events_count, events.size),
    )
    events.take(50).forEach { event ->
        HistoryRow(
            event = event,
            label = stringResource(R.string.details_movie),
            onEditDate = { onEditDate(event) },
            onDelete = { onDelete(event) },
        )
    }
}

@Composable
private fun HistoryRow(
    event: WatchEvent,
    label: String,
    onEditDate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${event.localDate.formatMedium()} · ${eventTypeLabel(event.eventType)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEditDate) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.details_edit_date),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.delete),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SynopsisSection(synopsis: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(R.string.synopsis),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = synopsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (synopsis.length > 220) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) R.string.show_less else R.string.show_more,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReviewSection(
    media: Media,
    revealSpoiler: Boolean,
    onToggleSpoiler: () -> Unit,
    onEdit: () -> Unit,
) {
    val hasReview = !media.review.isNullOrBlank()
    val hasNotes = !media.personalNotes.isNullOrBlank()
    if (!hasReview && !hasNotes && media.personalRating == null) {
        SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.details_review),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.details_review_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onEdit) {
                    Text(stringResource(R.string.details_write_review))
                }
            }
        }
        return
    }

    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.details_review),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.edit))
                }
            }
            RatingStars(media.personalRating)
            if (hasReview) {
                Spacer(Modifier.height(6.dp))
                if (media.reviewIsSpoiler && !revealSpoiler) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.details_spoiler_warning),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onToggleSpoiler) {
                                Text(stringResource(R.string.details_show_spoiler))
                            }
                        }
                    }
                } else {
                    Text(
                        text = media.review.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (media.reviewIsSpoiler) {
                        TextButton(onClick = onToggleSpoiler) {
                            Text(stringResource(R.string.details_hide_spoiler))
                        }
                    }
                }
            }
            if (hasNotes) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.details_notes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = media.personalNotes.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            media.reviewUpdatedAt?.let { updated ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.details_review_updated,
                        java.time.Instant.ofEpochMilli(updated)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .formatMedium(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewDialog(
    media: Media,
    onDismiss: () -> Unit,
    onSave: (Double?, String?, Boolean, String?) -> Unit,
) {
    var hasRating by remember { mutableStateOf(media.personalRating != null) }
    var rating by remember { mutableFloatStateOf((media.personalRating ?: 7.0).toFloat()) }
    var review by remember { mutableStateOf(media.review.orEmpty()) }
    var spoiler by remember { mutableStateOf(media.reviewIsSpoiler) }
    var notes by remember { mutableStateOf(media.personalNotes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_review)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.details_rating),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (hasRating) {
                        Text(
                            text = "${formatRating(rating.toDouble())} / 10",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = hasRating,
                        onCheckedChange = { checked -> hasRating = checked },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hasRating) {
                            stringResource(R.string.details_rating)
                        } else {
                            stringResource(R.string.details_no_rating)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (hasRating) {
                    Slider(
                        value = rating,
                        onValueChange = { rating = it },
                        valueRange = 0f..10f,
                        steps = 19,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.details_review)) },
                    placeholder = { Text(stringResource(R.string.details_review_hint)) },
                    minLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.details_spoiler),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = spoiler, onCheckedChange = { spoiler = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.details_notes)) },
                    placeholder = { Text(stringResource(R.string.details_notes_hint)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        if (hasRating) rating.toDouble() else null,
                        review.trim().takeIf { it.isNotEmpty() },
                        spoiler,
                        notes.trim().takeIf { it.isNotEmpty() },
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun EditTitleDialog(
    media: Media,
    onDismiss: () -> Unit,
    onSave: (String, MediaType, Int?, Int?, Int?) -> Unit,
) {
    var title by remember { mutableStateOf(media.title) }
    var type by remember { mutableStateOf(media.type) }
    var seasons by remember { mutableStateOf(media.totalSeasons?.toString().orEmpty()) }
    var episodes by remember { mutableStateOf(media.totalEpisodes?.toString().orEmpty()) }
    var runtime by remember { mutableStateOf(media.runtimeMinutes?.toString().orEmpty()) }
    var typeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_edit_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.details_title)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { typeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(mediaTypeLabel(type), modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        MediaType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(mediaTypeLabel(option)) },
                                onClick = {
                                    type = option
                                    typeMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = seasons,
                    onValueChange = { value -> seasons = value.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.details_total_seasons)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = episodes,
                    onValueChange = { value -> episodes = value.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.details_total_episodes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = runtime,
                    onValueChange = { value -> runtime = value.filter { it.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.details_runtime_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.details_local_edit_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        type,
                        seasons.toIntOrNull(),
                        episodes.toIntOrNull(),
                        runtime.toIntOrNull(),
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ListPickerDialog(
    lists: List<MediaList>,
    memberships: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_add_to_list)) },
        text = {
            if (lists.isEmpty()) {
                Text(stringResource(R.string.lists_empty_title))
            } else {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    lists.forEach { list ->
                        val member = list.id in memberships
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(list.id, member) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = member,
                                onCheckedChange = { onToggle(list.id, member) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(mediaListName(list), style = MaterialTheme.typography.bodyMedium)
                                if (list.description != null) {
                                    Text(
                                        text = list.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
