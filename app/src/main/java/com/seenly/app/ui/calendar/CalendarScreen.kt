package com.seenly.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.ActivityItem
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.UpcomingEpisode
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.SectionHeader
import com.seenly.app.ui.components.eventTypeLabel
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatMonthYear
import com.seenly.app.ui.seenlyViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
fun CalendarScreen(
    container: AppContainer,
    onOpenMedia: (Long) -> Unit,
    viewModel: CalendarViewModel = seenlyViewModel { CalendarViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.calendar_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::goToToday) {
                    Icon(Icons.Rounded.Today, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.calendar_today))
                }
            }
        }
        if (errorText != null) {
            item {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item {
            CalendarCard(
                month = state.month,
                selected = state.selectedDate,
                today = state.today,
                data = state.data,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onSelect = viewModel::selectDate,
            )
        }
        item {
            Text(
                text = state.selectedDate.formatMedium(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val activities = state.data.activityByDate[state.selectedDate].orEmpty()
        val upcoming = state.data.upcomingEpisodesByDate[state.selectedDate].orEmpty()
        val releases = state.data.upcomingReleasesByDate[state.selectedDate].orEmpty()
        if (activities.isEmpty() && upcoming.isEmpty() && releases.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.calendar_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        if (activities.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.calendar_activity)) }
            items(activities.size) { index ->
                val activity = activities[index]
                ActivityRow(activity = activity, onClick = { onOpenMedia(activity.mediaId) })
            }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.calendar_upcoming)) }
            items(upcoming.size) { index ->
                val episode = upcoming[index]
                UpcomingRow(episode = episode, onClick = { onOpenMedia(episode.mediaId) })
            }
        }
        if (releases.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.calendar_releases)) }
            items(releases.size) { index ->
                val entry = releases[index]
                ReleaseRow(entry = entry, onClick = { onOpenMedia(entry.media.id) })
            }
        }

        val future = state.data.upcomingEpisodesByDate
            .filterKeys { it.isAfter(state.today) }
            .toSortedMap()
            .entries
            .take(8)
        if (future.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.calendar_next_days),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            future.forEach { (date, episodes) ->
                item(key = "future_$date") {
                    Text(
                        text = date.formatMedium(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(episodes.size, key = { index -> "future_${date}_${episodes[index].episodeId}" }) { index ->
                    val episode = episodes[index]
                    UpcomingRow(episode = episode, onClick = { onOpenMedia(episode.mediaId) })
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    month: java.time.YearMonth,
    selected: LocalDate,
    today: LocalDate,
    data: com.seenly.app.domain.model.CalendarData,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    text = month.formatMonthYear(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.next_month))
                }
            }
            val firstDay = WeekFields.of(locale).firstDayOfWeek
            val weekdays = (0..6).map { offset ->
                DayOfWeek.of(((firstDay.value - 1 + offset) % 7) + 1)
            }
            Row(Modifier.fillMaxWidth()) {
                weekdays.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
            val totalCells = leading + month.lengthOfMonth()
            val rows = (totalCells + 6) / 7
            repeat(rows) { row ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val index = row * 7 + column
                        val dayNumber = index - leading + 1
                        if (dayNumber in 1..month.lengthOfMonth()) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                selected = date == selected,
                                today = date == today,
                                activityCount = data.activityByDate[date]?.size ?: 0,
                                upcomingCount = (data.upcomingEpisodesByDate[date]?.size ?: 0) +
                                    (data.upcomingReleasesByDate[date]?.size ?: 0),
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    today: Boolean,
    activityCount: Int,
    upcomingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .then(
                if (today && !selected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (activityCount > 0) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                if (upcomingCount > 0) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivityItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = activity.mediaTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val episode = if (activity.episodeNumber != null) {
                stringResource(R.string.calendar_episode_label, activity.episodeNumber)
            } else {
                stringResource(R.string.details_movie)
            }
            Text(
                text = "$episode · ${eventTypeLabel(activity.eventType)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpcomingRow(episode: UpcomingEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.mediaTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.calendar_episode_label, episode.episodeNumber ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseRow(entry: LibraryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.media.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mediaTypeLabel(entry.media.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
