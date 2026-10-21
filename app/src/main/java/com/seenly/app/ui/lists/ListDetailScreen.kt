package com.seenly.app.ui.lists

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.LibraryEntry
import com.seenly.app.domain.model.WatchStatus
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.ConfirmDialog
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.PosterImage
import com.seenly.app.ui.components.mediaListName
import com.seenly.app.ui.components.watchStatusLabel
import com.seenly.app.ui.seenlyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    container: AppContainer,
    listId: Long,
    onBack: () -> Unit,
    onOpenMedia: (Long) -> Unit,
    viewModel: ListDetailViewModel = seenlyViewModel(key = "list_$listId") {
        ListDetailViewModel(container, listId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showBulkConfirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = state.list?.let { mediaListName(it) }.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (state.list != null && !state.isPlanList) {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_titles))
                    }
                }
            },
        )

        if (errorText != null) {
            ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
        }

        if (state.list?.description != null) {
            Text(
                text = state.list?.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (state.isPlanList) {
            Text(
                text = stringResource(R.string.list_plan_to_watch_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.items.isNotEmpty() || state.bulkCompletedCount != null) {
            BulkCompleteSection(
                itemCount = state.items.size,
                completing = state.bulkCompleting,
                completedCount = state.bulkCompletedCount,
                onRequest = { showBulkConfirm = true },
                onDismissResult = viewModel::dismissBulkResult,
            )
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                title = stringResource(R.string.list_empty_title),
                message = stringResource(R.string.list_empty_message),
                actionLabel = if (state.isPlanList) null else stringResource(R.string.add_titles),
                onAction = if (state.isPlanList) null else ({ showAdd = true }),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.items, key = { it.media.id }) { entry ->
                    ListItemRow(
                        entry = entry,
                        canReorder = !state.isPlanList,
                        canRemove = !state.isPlanList,
                        onOpen = { onOpenMedia(entry.media.id) },
                        onMarkCompleted = { viewModel.markCompleted(entry) },
                        onSetStatus = { status -> viewModel.setStatus(entry, status) },
                        onMoveUp = { viewModel.move(entry, -1) },
                        onMoveDown = { viewModel.move(entry, 1) },
                        onRemove = { viewModel.remove(entry) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddTitlesDialog(
            entries = state.available,
            onAdd = viewModel::add,
            onOpenMedia = onOpenMedia,
            onDismiss = { showAdd = false },
        )
    }

    if (showBulkConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.list_mark_completed_title),
            text = stringResource(R.string.list_mark_completed_message, state.items.size),
            confirmLabel = stringResource(R.string.list_mark_completed_confirm),
            onConfirm = {
                showBulkConfirm = false
                viewModel.markAllCompleted()
            },
            onDismiss = { showBulkConfirm = false },
        )
    }
}

@Composable
private fun BulkCompleteSection(
    itemCount: Int,
    completing: Boolean,
    completedCount: Int?,
    onRequest: () -> Unit,
    onDismissResult: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (itemCount > 0) {
            FilledTonalButton(onClick = onRequest, enabled = !completing) {
                if (completing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.list_mark_completed, itemCount))
            }
        }
        if (completedCount != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (completedCount > 0) {
                        stringResource(R.string.list_mark_completed_done, completedCount)
                    } else {
                        stringResource(R.string.list_mark_completed_none)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismissResult) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        }
    }
}

@Composable
private fun ListItemRow(
    entry: LibraryEntry,
    canReorder: Boolean,
    canRemove: Boolean,
    onOpen: () -> Unit,
    onMarkCompleted: () -> Unit,
    onSetStatus: (WatchStatus) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = entry.media.posterUrl,
                contentDescription = entry.media.title,
                modifier = Modifier
                    .width(48.dp)
                    .height(72.dp)
                    .clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.media.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showStatusMenu = true }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = watchStatusLabel(entry.status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = stringResource(R.string.status_change),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
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
                                    onSetStatus(option)
                                },
                            )
                        }
                    }
                }
            }
            if (entry.status != WatchStatus.COMPLETED) {
                IconButton(onClick = onMarkCompleted) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.list_mark_title_completed),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (canReorder) {
                IconButton(onClick = onMoveUp) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.move_up),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onMoveDown) {
                    Icon(
                        Icons.Rounded.ArrowDownward,
                        contentDescription = stringResource(R.string.move_down),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove_from_list),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTitlesDialog(
    entries: List<LibraryEntry>,
    onAdd: (LibraryEntry) -> Unit,
    onOpenMedia: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = entries.filter { entry ->
        query.isBlank() || entry.media.title.contains(query.trim(), ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_titles)) },
        text = {
            Column(Modifier.heightIn(max = 440.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_library)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_matching_titles),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        filtered.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenMedia(entry.media.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PosterImage(
                                    url = entry.media.posterUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(34.dp)
                                        .height(50.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = entry.media.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onAdd(entry) }) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.add_to_list),
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
