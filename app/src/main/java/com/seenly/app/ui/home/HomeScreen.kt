package com.seenly.app.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.ContinueWatchingItem
import com.seenly.app.domain.model.HomeSectionKey
import com.seenly.app.domain.model.MediaType
import com.seenly.app.domain.model.UpcomingEpisode
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.MediaCard
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.SectionHeader
import com.seenly.app.ui.components.StatusPill
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatNumber
import com.seenly.app.ui.recommendations.RecommendationDeck
import com.seenly.app.ui.recommendations.RecommendationDeckViewModel
import com.seenly.app.ui.recommendations.RecommendationDetailsSheet
import com.seenly.app.ui.seenlyViewModel
import com.seenly.app.ui.formatHours

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenMedia: (Long) -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenRecommendations: () -> Unit,
    viewModel: HomeViewModel = seenlyViewModel { HomeViewModel(container) },
    deckViewModel: RecommendationDeckViewModel = seenlyViewModel {
        RecommendationDeckViewModel(container)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deckState by deckViewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (errorText != null) {
            item {
                ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
            }
        }
        if (state.librarySize == 0) {
            item {
                WelcomeCard(onOpenDiscover = onOpenDiscover)
            }
        }
        if (HomeSectionKey.QUICK_STATS in state.sections) {
            item {
                QuickStatsRow(state = state)
            }
        }
        if (HomeSectionKey.CONTINUE_WATCHING in state.sections && state.continueWatching.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.home_continue_watching),
                    actionLabel = stringResource(R.string.view_library),
                ) {}
            }
            items(state.continueWatching, key = { "continue_${it.entry.media.id}" }) { item ->
                ContinueWatchingCard(
                    item = item,
                    onOpen = { onOpenMedia(item.entry.media.id) },
                    onMarkWatched = { viewModel.markNextEpisode(item) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (HomeSectionKey.UPCOMING_EPISODES in state.sections && state.upcoming.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_upcoming))
            }
            items(state.upcoming.take(6), key = { "upcoming_${it.episodeId}" }) { episode ->
                UpcomingEpisodeRow(
                    episode = episode,
                    onClick = { onOpenMedia(episode.mediaId) },
                )
            }
        }
        if (HomeSectionKey.RECENTLY_WATCHED in state.sections && state.recent.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_recently_watched))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.recent, key = { "activity_${it.eventId}" }) { activity ->
                        RecentActivityCard(
                            activity = activity,
                            onClick = { onOpenMedia(activity.mediaId) },
                        )
                    }
                }
            }
        }
        if (HomeSectionKey.WATCHLIST in state.sections && state.watchlist.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_watchlist))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.watchlist, key = { "watchlist_${it.media.id}" }) { entry ->
                        MediaCard(
                            entry = entry,
                            onClick = { onOpenMedia(entry.media.id) },
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }
            }
        }
        if (HomeSectionKey.RECOMMENDATIONS in state.sections) {
            item {
                SectionHeader(
                    title = stringResource(R.string.home_recommendations),
                    actionLabel = stringResource(R.string.view_all),
                    onAction = onOpenRecommendations,
                )
            }
            item {
                RecommendationDeck(
                    state = deckState,
                    onLike = deckViewModel::like,
                    onDislike = deckViewModel::dislike,
                    onOpenDetails = deckViewModel::openDetails,
                    onRetry = deckViewModel::load,
                    onDismissAdded = deckViewModel::dismissAdded,
                    onDismissError = deckViewModel::dismissError,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        if (HomeSectionKey.ACTIVITY_CALENDAR in state.sections) {
            item {
                StreakCard(
                    currentStreak = state.stats.currentStreak,
                    longestStreak = state.stats.longestStreak,
                    onOpenCalendar = onOpenCalendar,
                )
            }
        }
    }

    RecommendationDetailsSheet(
        state = deckState,
        onDismiss = deckViewModel::closeDetails,
        onAdd = { deckState.selectedCard?.let(deckViewModel::like) },
        onDismissError = deckViewModel::dismissSelectedError,
    )
}

@Composable
private fun WelcomeCard(onOpenDiscover: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_empty_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenDiscover) {
                Icon(Icons.Rounded.Explore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_browse_titles))
            }
        }
    }
}

@Composable
private fun QuickStatsRow(state: HomeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCell(
            value = formatNumber(state.stats.totalViewings),
            label = stringResource(R.string.stat_viewings),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            value = formatHours(state.stats.estimatedMinutes),
            label = stringResource(R.string.stat_hours),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            value = formatNumber(state.stats.completedEpisodes),
            label = stringResource(R.string.stat_episodes),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onOpen: () -> Unit,
    onMarkWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(10.dp)) {
            PosterImage(
                url = item.entry.media.posterUrl,
                contentDescription = item.entry.media.title,
                modifier = Modifier
                    .width(76.dp)
                    .height(108.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.entry.media.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = episodeLabel(item.nextEpisode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.entry.progressFraction ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(item.entry.status)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onMarkWatched) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.mark_episode_watched),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun episodeLabel(episode: com.seenly.app.domain.model.Episode?): String {
    if (episode == null) return stringResource(R.string.home_no_next_episode)
    val season = episode.seasonId
    val number = episode.number
    return if (number != null) {
        stringResource(R.string.episode_number, number)
    } else {
        stringResource(R.string.next_episode)
    }
}

@Composable
private fun UpcomingEpisodeRow(episode: UpcomingEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = episode.airDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.mediaTitle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.episode_number_generic, episode.episodeNumber ?: 0))
                    append(" · ")
                    append(episode.airDate.formatMedium())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentActivityCard(activity: ActivityItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(8.dp)) {
            PosterImage(
                url = activity.posterUrl,
                contentDescription = activity.mediaTitle,
                modifier = Modifier
                    .width(48.dp)
                    .height(70.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = activity.mediaTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (activity.episodeNumber != null) {
                        stringResource(R.string.episode_number_generic, activity.episodeNumber)
                    } else {
                        stringResource(R.string.movie_label)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = activity.localDate.formatMedium(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StreakCard(currentStreak: Int, longestStreak: Int, onOpenCalendar: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_streak, currentStreak),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_longest_streak, longestStreak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpenCalendar) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_calendar))
            }
        }
    }
}
