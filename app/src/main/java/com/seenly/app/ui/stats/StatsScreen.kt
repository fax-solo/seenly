package com.seenly.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.GoalType
import com.seenly.app.domain.model.MediaCategory
import com.seenly.app.domain.statistics.CategoryCount
import com.seenly.app.domain.statistics.CompletedTitle
import com.seenly.app.domain.statistics.MonthlyViewing
import com.seenly.app.domain.statistics.NameCount
import com.seenly.app.domain.statistics.RatedTitle
import com.seenly.app.domain.statistics.StatisticsSnapshot
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.SectionHeader
import com.seenly.app.ui.components.SurfaceCard
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatNumber
import com.seenly.app.ui.formatHours
import com.seenly.app.ui.formatRating
import com.seenly.app.ui.seenlyViewModel
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

@Composable
fun StatsScreen(
    container: AppContainer,
    onOpenMedia: (Long) -> Unit,
    onOpenGoals: () -> Unit,
    viewModel: StatsViewModel = seenlyViewModel { StatsViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snapshot = state.snapshot
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
                    text = stringResource(R.string.stats_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                val range = snapshot.firstActivityDate?.let { first ->
                    snapshot.lastActivityDate?.let { last ->
                        stringResource(
                            R.string.stats_range,
                            first.formatMedium(),
                            last.formatMedium(),
                        )
                    }
                }
                Text(
                    text = range ?: stringResource(R.string.stats_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

        if (!snapshot.hasData) {
            item {
                EmptyState(
                    icon = Icons.Rounded.BarChart,
                    title = stringResource(R.string.stats_empty_title),
                    message = stringResource(R.string.stats_empty_message),
                )
            }
        } else {
            item {
                OverviewCard(snapshot)
            }
            if (snapshot.viewingsWithUnknownRuntime > 0) {
                item {
                    Text(
                        text = stringResource(
                            R.string.stats_unknown_runtime,
                            snapshot.viewingsWithUnknownRuntime,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (snapshot.monthly.isNotEmpty()) {
                item {
                    MonthlyChartCard(snapshot.monthly)
                }
            }
            if (snapshot.genreDistribution.isNotEmpty()) {
                item {
                    GenreCard(snapshot.genreDistribution)
                }
            }
            if (snapshot.categoryDistribution.isNotEmpty()) {
                item {
                    CategoryCard(snapshot.categoryDistribution)
                }
            }
            item {
                StreakCard(
                    current = snapshot.currentStreak,
                    longest = snapshot.longestStreak,
                    averageRating = snapshot.averageRating,
                    ratedCount = snapshot.ratedCount,
                    dailyActivity = snapshot.dailyActivity,
                )
            }
            if (state.goals.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.stats_goals),
                        actionLabel = stringResource(R.string.stats_view_goals),
                        onAction = onOpenGoals,
                    )
                }
                items(state.goals, key = { "goal_${it.goal.id}" }) { progress ->
                    GoalPreview(progress)
                }
            }
            if (snapshot.highestRated.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.stats_highest_rated))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(snapshot.highestRated, key = { "rated_${it.mediaId}" }) { rated ->
                            RatedCard(rated, onClick = { onOpenMedia(rated.mediaId) })
                        }
                    }
                }
            }
            if (snapshot.recentlyCompleted.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.stats_recently_completed))
                }
                items(snapshot.recentlyCompleted, key = { "completed_${it.mediaId}" }) { completed ->
                    CompletedRow(completed, onClick = { onOpenMedia(completed.mediaId) })
                }
            }
            item {
                Text(
                    text = stringResource(R.string.stats_offline_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(snapshot: StatisticsSnapshot) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Row {
                StatBlock(
                    value = formatNumber(snapshot.completedMovies + snapshot.completedSeries),
                    label = stringResource(R.string.stat_completed_titles),
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = formatNumber(snapshot.completedEpisodes),
                    label = stringResource(R.string.stat_completed_episodes),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                StatBlock(
                    value = formatNumber(snapshot.totalViewings),
                    label = stringResource(R.string.stat_viewings),
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    value = formatHours(snapshot.estimatedMinutes),
                    label = stringResource(R.string.stat_hours),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_library_titles, snapshot.totalLibraryTitles),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthlyChartCard(monthly: List<MonthlyViewing>) {
    val locale = LocalConfiguration.current.locales[0]
    val visible = monthly.takeLast(12)
    val max = visible.maxOf { it.episodeViewings + it.movieViewings }.coerceAtLeast(1)
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Text(
                text = stringResource(R.string.stats_monthly),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.stats_monthly_total,
                    visible.sumOf { it.episodeViewings + it.movieViewings },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visible.forEach { month ->
                    val viewings = month.episodeViewings + month.movieViewings
                    val fraction = viewings.toFloat() / max
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.62f)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 3.dp,
                                            topEnd = 3.dp,
                                        ),
                                    )
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Month.of(month.month)
                                .getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(genres: List<NameCount>) {
    val max = genres.maxOf { it.count }
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Text(
                text = stringResource(R.string.stats_genres),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            genres.take(6).forEach { genre ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = genre.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(110.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { genre.count.toFloat() / max.coerceAtLeast(1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatNumber(genre.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CategoryCard(categories: List<CategoryCount>) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Text(
                text = stringResource(R.string.stats_categories),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = "${statsCategoryLabel(category.category)} · ${formatNumber(category.count)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakCard(
    current: Int,
    longest: Int,
    averageRating: Double?,
    ratedCount: Int,
    dailyActivity: Map<LocalDate, Int>,
) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_streak_current, current),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.stats_streak_longest, longest),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (averageRating != null) {
                        stringResource(
                            R.string.stats_average_rating,
                            formatRating(averageRating) ?: "",
                            ratedCount,
                        )
                    } else {
                        stringResource(R.string.stats_no_rating)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            ActivityHeatmap(dailyActivity)
        }
    }
}

@Composable
private fun ActivityHeatmap(dailyActivity: Map<LocalDate, Int>) {
    val end = LocalDate.now()
    val start = end.minusDays(118)
    val days = (0..118).map { start.plusDays(it.toLong()) }
    val weeks = days.chunked(7)
    val max = dailyActivity.values.maxOrNull() ?: 0
    Text(
        text = stringResource(R.string.stats_activity),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { date ->
                    val count = dailyActivity[date] ?: 0
                    val level = when {
                        count == 0 || max == 0 -> 0
                        count >= max -> 4
                        count * 4 >= max * 3 -> 3
                        count * 2 >= max -> 2
                        else -> 1
                    }
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(heatColor(level)),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.stats_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (0..4).forEach { level ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(heatColor(level)),
            )
        }
        Text(
            text = stringResource(R.string.stats_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Five-step scale relative to the busiest day in the shown window. */
@Composable
private fun heatColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun GoalPreview(progress: GoalProgress) {
    SurfaceCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${goalTypeLabel(progress.goal.type)} · ${goalMetricLabel(progress.goal.metric)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${progress.current} / ${progress.goal.target}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            )
        }
    }
}

@Composable
private fun RatedCard(rated: RatedTitle, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        PosterImage(
            url = rated.posterUrl,
            contentDescription = rated.title,
            modifier = Modifier
                .width(120.dp)
                .height(168.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = rated.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatRating(rated.rating) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CompletedRow(completed: CompletedTitle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterImage(
            url = completed.posterUrl,
            contentDescription = completed.title,
            modifier = Modifier
                .width(40.dp)
                .height(58.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = completed.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val date = completed.completedOn ?: completed.lastActivity
            if (date != null) {
                Text(
                    text = date.formatMedium(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun statsCategoryLabel(category: MediaCategory): String = stringResource(
    when (category) {
        MediaCategory.ANIME -> R.string.category_anime
        MediaCategory.MOVIES -> R.string.category_movies
        MediaCategory.SERIES -> R.string.category_series
        MediaCategory.CARTOONS -> R.string.category_cartoons
    },
)

@Composable
private fun goalTypeLabel(type: GoalType): String = stringResource(
    when (type) {
        GoalType.WEEKLY -> R.string.goal_type_weekly
        GoalType.MONTHLY -> R.string.goal_type_monthly
        GoalType.YEARLY -> R.string.goal_type_yearly
    },
)

@Composable
private fun goalMetricLabel(metric: GoalMetric): String = stringResource(
    when (metric) {
        GoalMetric.EPISODES -> R.string.goal_metric_episodes
        GoalMetric.MOVIES -> R.string.goal_metric_movies
        GoalMetric.TITLES -> R.string.goal_metric_titles
        GoalMetric.MINUTES -> R.string.goal_metric_minutes
    },
)
