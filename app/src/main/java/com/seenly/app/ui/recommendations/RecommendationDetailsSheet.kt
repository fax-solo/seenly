package com.seenly.app.ui.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seenly.app.R
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.formatRating

/**
 * Provider preview for a For You card. Opening this must not import the title;
 * the user can inspect ratings, synopsis and every franchise season first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationDetailsSheet(
    state: RecommendationDeckUiState,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onDismissError: () -> Unit,
) {
    val card = state.selectedCard ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            val media = state.selectedDetails?.media ?: card
            Row {
                PosterImage(
                    url = media.posterUrl,
                    contentDescription = media.title,
                    modifier = Modifier
                        .width(110.dp)
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleLarge,
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
                    Text(
                        text = mediaTypeLabel(media.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    media.releaseDate?.let { date ->
                        Text(
                            text = date.formatMedium(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    media.score?.let { score ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatRating(score).orEmpty(),
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

            val errorText = appErrorText(state.selectedError)
            if (errorText != null) {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(message = errorText, onDismiss = onDismissError)
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
                val ratings = buildList {
                    details.media.imdbRating?.let {
                        add(stringResource(R.string.rating_imdb) + " " + formatRating(it))
                    }
                    details.media.rottenTomatoesRating?.let {
                        add(stringResource(R.string.rating_rotten_tomatoes) + " $it%")
                    }
                    details.media.malRating?.let {
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.discover_episodes_count, episodeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            (details?.media?.synopsis ?: media.synopsis)?.takeIf { it.isNotBlank() }?.let { synopsis ->
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
                        text = card.provider.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onAdd, enabled = !state.busy) {
                        if (state.busy) {
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.discover_data_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
