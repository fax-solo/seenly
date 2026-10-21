package com.seenly.app.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.RatingStars
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.seenlyViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ReviewsViewModel(container: AppContainer) : ViewModel() {
    val state = container.libraryRepository.observeLibrary()
        .map { entries ->
            entries.filter { !it.media.review.isNullOrBlank() }
                .sortedByDescending { it.media.reviewUpdatedAt ?: it.media.reviewCreatedAt ?: 0L }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}

@Composable
fun ReviewsScreen(
    container: AppContainer,
    onOpenMedia: (Long) -> Unit,
    viewModel: ReviewsViewModel = seenlyViewModel { ReviewsViewModel(container) },
) {
    val reviews by viewModel.state.collectAsStateWithLifecycle()
    val revealed = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.reviews_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.reviews_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (reviews.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.RateReview,
                    title = stringResource(R.string.reviews_empty_title),
                    message = stringResource(R.string.reviews_empty_message),
                )
            }
        } else {
            items(reviews, key = { it.media.id }) { entry ->
                ReviewCard(
                    entry = entry,
                    revealed = revealed[entry.media.id] == true,
                    onReveal = { revealed[entry.media.id] = true },
                    onClick = { onOpenMedia(entry.media.id) },
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    entry: LibraryEntry,
    revealed: Boolean,
    onReveal: () -> Unit,
    onClick: () -> Unit,
) {
    val media = entry.media
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(12.dp)) {
            PosterImage(
                url = media.posterUrl,
                contentDescription = media.title,
                modifier = Modifier
                    .width(62.dp)
                    .height(93.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RatingStars(media.personalRating)
                media.reviewUpdatedAt?.let { updated ->
                    Text(
                        text = stringResource(
                            R.string.reviews_updated,
                            java.time.Instant.ofEpochMilli(updated)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .formatMedium(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (media.reviewIsSpoiler && !revealed) {
                    Text(
                        text = stringResource(R.string.details_spoiler_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onReveal) {
                        Text(stringResource(R.string.details_show_spoiler))
                    }
                } else {
                    Text(
                        text = media.review.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
