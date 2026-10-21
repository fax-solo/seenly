package com.seenly.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seenly.app.BuildConfig
import com.seenly.app.R
import com.seenly.app.ui.components.SurfaceCard

@Composable
fun MoreScreen(
    onOpenLists: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.more_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.more_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            MoreRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                title = stringResource(R.string.lists_title),
                subtitle = stringResource(R.string.more_lists_subtitle),
                onClick = onOpenLists,
            )
        }
        item {
            MoreRow(
                icon = Icons.Rounded.CalendarMonth,
                title = stringResource(R.string.calendar_title),
                subtitle = stringResource(R.string.more_calendar_subtitle),
                onClick = onOpenCalendar,
            )
        }
        item {
            MoreRow(
                icon = Icons.Rounded.EmojiEvents,
                title = stringResource(R.string.goals_title),
                subtitle = stringResource(R.string.more_goals_subtitle),
                onClick = onOpenGoals,
            )
        }
        item {
            MoreRow(
                icon = Icons.Rounded.RateReview,
                title = stringResource(R.string.reviews_title),
                subtitle = stringResource(R.string.more_reviews_subtitle),
                onClick = onOpenReviews,
            )
        }
        item {
            MoreRow(
                icon = Icons.Rounded.Backup,
                title = stringResource(R.string.backup_title),
                subtitle = stringResource(R.string.more_backup_subtitle),
                onClick = onOpenBackup,
            )
        }
        item {
            MoreRow(
                icon = Icons.Rounded.Settings,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.more_settings_subtitle),
                onClick = onOpenSettings,
            )
        }
        item {
            SurfaceCard(modifier = Modifier.padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.more_about_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.more_about_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.more_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SurfaceCard {
                Text(
                    text = stringResource(R.string.more_attribution_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.more_attribution_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
