package com.seenly.app.ui.recommendations

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatRating
import com.seenly.app.ui.seenlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    viewModel: RecommendationDeckViewModel = seenlyViewModel {
        RecommendationDeckViewModel(container)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)
    val snackbarHostState = remember { SnackbarHostState() }
    val addedMessage = state.addedTitle?.let { title ->
        stringResource(R.string.deck_added_to_library, title)
    }

    LaunchedEffect(addedMessage) {
        if (addedMessage != null) {
            snackbarHostState.showSnackbar(addedMessage)
            viewModel.dismissAdded()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_recommendations)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (errorText != null) {
                ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.cards.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.deck_empty_title),
                    message = stringResource(R.string.deck_empty_message),
                    actionLabel = stringResource(R.string.details_refresh),
                    onAction = viewModel::load,
                    modifier = Modifier.weight(1f),
                )

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.cards, key = { it.deckKey }) { card ->
                        RecommendationRow(
                            card = card,
                            enabled = !state.busy,
                            onOpenDetails = { viewModel.openDetails(card) },
                            onLike = { viewModel.like(card) },
                            onDislike = { viewModel.dislike(card) },
                        )
                    }
                }
            }
        }
    }

    RecommendationDetailsSheet(
        state = state,
        onDismiss = viewModel::closeDetails,
        onAdd = { state.selectedCard?.let(viewModel::like) },
        onDismissError = viewModel::dismissSelectedError,
    )
}

@Composable
private fun RecommendationRow(
    card: ProviderMedia,
    enabled: Boolean,
    onOpenDetails: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenDetails)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = card.posterUrl,
                contentDescription = card.title,
                modifier = Modifier
                    .width(58.dp)
                    .height(86.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(mediaTypeLabel(card.type))
                        card.releaseDate?.let { append(" · ${it.year}") }
                        card.score?.let { score ->
                            append(" · ")
                            append(formatRating(score).orEmpty())
                            append(" ★")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.genres.isNotEmpty()) {
                    Text(
                        text = card.genres.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDislike, enabled = enabled) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.deck_not_interested),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onLike, enabled = enabled) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.deck_add_to_library),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
