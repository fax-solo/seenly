package com.seenly.app.ui.recommendations

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.seenly.app.R
import com.seenly.app.domain.providers.ProviderMedia
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatRating
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Swipeable recommendation card. The artwork sits in the middle, a right swipe
 * (or the primary button) adds the title to the library, a left swipe dismisses
 * it for good.
 */
@Composable
fun RecommendationDeck(
    state: RecommendationDeckUiState,
    onLike: (ProviderMedia) -> Unit,
    onDislike: (ProviderMedia) -> Unit,
    onOpenDetails: (ProviderMedia) -> Unit,
    onRetry: () -> Unit,
    onDismissAdded: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorText = appErrorText(state.error)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (errorText != null) {
            ErrorBanner(message = errorText, onDismiss = onDismissError)
        }
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.cards.isEmpty() -> EmptyDeck(onRetry = onRetry)

            else -> DeckStack(
                state = state,
                onLike = onLike,
                onDislike = onDislike,
                onOpenDetails = onOpenDetails,
                onDismissAdded = onDismissAdded,
            )
        }
    }
}

@Composable
private fun EmptyDeck(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.deck_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.deck_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.details_refresh))
            }
        }
    }
}

@Composable
private fun DeckStack(
    state: RecommendationDeckUiState,
    onLike: (ProviderMedia) -> Unit,
    onDislike: (ProviderMedia) -> Unit,
    onOpenDetails: (ProviderMedia) -> Unit,
    onDismissAdded: () -> Unit,
) {
    val card = state.cards.first()
    val next = state.cards.getOrNull(1)
    val offsetX = remember(card.deckKey) { Animatable(0f) }
    var dragging by remember(card.deckKey) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(448.dp)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (next != null) {
                RecommendationFace(
                    media = next,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .graphicsLayer {
                            scaleX = 0.94f
                            scaleY = 0.94f
                            alpha = 0.55f
                            translationY = 18.dp.toPx()
                        },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer { rotationZ = offsetX.value / 70f }
                    .pointerInput(card.deckKey) {
                        detectHorizontalDragGestures(
                            onDragCancel = {
                                dragging = 0f
                                scope.launch { offsetX.animateTo(0f) }
                            },
                            onDragEnd = {
                                val value = dragging
                                dragging = 0f
                                when {
                                    value > threshold -> {
                                        onLike(card)
                                        scope.launch { offsetX.animateTo(0f) }
                                    }

                                    value < -threshold -> {
                                        onDislike(card)
                                        scope.launch { offsetX.animateTo(0f) }
                                    }

                                    else -> scope.launch { offsetX.animateTo(0f) }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dragging += dragAmount
                                scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                            },
                        )
                    },
            ) {
                RecommendationFace(
                    media = card,
                    onClick = { onOpenDetails(card) },
                    modifier = Modifier.fillMaxSize(),
                )
                if (dragging > 24f || offsetX.value > threshold) {
                    SwipeBadge(
                        text = stringResource(R.string.deck_add_to_library),
                        positive = true,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                    )
                } else if (dragging < -24f || offsetX.value < -threshold) {
                    SwipeBadge(
                        text = stringResource(R.string.deck_not_interested),
                        positive = false,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                }
                if (state.busy) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(24.dp)
                                .size(32.dp),
                        )
                    }
                }
            }
        }

        if (state.addedTitle != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.deck_added_to_library, state.addedTitle),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissAdded) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(
                onClick = { onDislike(card) },
                enabled = !state.busy,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_not_interested))
            }
            Button(
                onClick = { onLike(card) },
                enabled = !state.busy,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.deck_add_to_library))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.deck_swipe_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeBadge(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = if (positive) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (positive) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RecommendationFace(
    media: ProviderMedia,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PosterImage(
                url = media.posterUrl,
                contentDescription = media.title,
                modifier = Modifier
                    .width(180.dp)
                    .height(264.dp)
                    .clip(MaterialTheme.shapes.large),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mediaTypeLabel(media.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                media.releaseDate?.let { date ->
                    Text(
                        text = " · ${date.year}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                media.score?.let { score ->
                    Text(
                        text = " · ${formatRating(score).orEmpty()} ★",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (media.genres.isNotEmpty()) {
                Text(
                    text = media.genres.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
