package com.seenly.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.model.GoalMetric
import com.seenly.app.domain.model.GoalProgress
import com.seenly.app.domain.model.GoalType
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.ConfirmDialog
import com.seenly.app.ui.components.EmptyState
import com.seenly.app.ui.components.SurfaceCard
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.seenlyViewModel

@Composable
fun GoalsScreen(
    container: AppContainer,
    viewModel: GoalsViewModel = seenlyViewModel { GoalsViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.error.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<GoalProgress?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<GoalProgress?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.goals_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.goals_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add_goal))
            }
        }

        val errorText = appErrorText(state.error ?: actionError)
        if (errorText != null) {
            ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.goals.isEmpty() -> EmptyState(
                icon = Icons.Rounded.EmojiEvents,
                title = stringResource(R.string.goals_empty_title),
                message = stringResource(R.string.goals_empty_message),
                actionLabel = stringResource(R.string.add_goal),
                onAction = { creating = true },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.goals, key = { it.goal.id }) { progress ->
                    GoalCard(
                        progress = progress,
                        onEdit = { editing = progress },
                        onDelete = { deleting = progress },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        GoalEditorDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { type, metric, target ->
                viewModel.saveGoal(type, metric, target)
                creating = false
                editing = null
            },
        )
    }

    deleting?.let { progress ->
        ConfirmDialog(
            title = stringResource(R.string.delete_goal),
            text = stringResource(R.string.delete_goal_message),
            confirmLabel = stringResource(R.string.delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteGoal(progress.goal.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GoalCard(
    progress: GoalProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = goalTypeLabel(progress.goal.type),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = goalMetricLabel(progress.goal.metric),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete_goal),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = progress.current.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " / ${progress.goal.target} ${goalUnitLabel(progress.goal.metric)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.goal_period,
                    progress.periodStart.formatMedium(),
                    progress.periodEnd.formatMedium(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.current >= progress.goal.target) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.goal_reached),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    initial: GoalProgress?,
    onDismiss: () -> Unit,
    onSave: (GoalType, GoalMetric, Int) -> Unit,
) {
    var type by remember { mutableStateOf(initial?.goal?.type ?: GoalType.WEEKLY) }
    var metric by remember { mutableStateOf(initial?.goal?.metric ?: GoalMetric.EPISODES) }
    var target by remember {
        mutableStateOf(initial?.goal?.target?.toString() ?: "5")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (initial == null) R.string.add_goal else R.string.edit_goal),
            )
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.goal_type), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoalType.entries) { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(goalTypeLabel(option)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.goal_metric), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GoalMetric.entries) { option ->
                        FilterChip(
                            selected = metric == option,
                            onClick = { metric = option },
                            label = { Text(goalMetricLabel(option)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { value -> target = value.filter { it.isDigit() }.take(7) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.goal_target, goalUnitLabel(metric))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.goal_target_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.goal_replace_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(type, metric, target.toIntOrNull() ?: 0) },
                enabled = (target.toIntOrNull() ?: 0) > 0,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

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

@Composable
private fun goalUnitLabel(metric: GoalMetric): String = stringResource(
    when (metric) {
        GoalMetric.EPISODES -> R.string.goal_unit_episodes
        GoalMetric.MOVIES -> R.string.goal_unit_movies
        GoalMetric.TITLES -> R.string.goal_unit_titles
        GoalMetric.MINUTES -> R.string.goal_unit_minutes
    },
)
