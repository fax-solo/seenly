package com.seenly.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seenly.app.AppContainer
import com.seenly.app.R
import com.seenly.app.domain.repository.BackupSummary
import com.seenly.app.domain.repository.ImportMode
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.SurfaceCard
import com.seenly.app.ui.seenlyViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    container: AppContainer,
    onBack: () -> Unit,
    viewModel: BackupViewModel = seenlyViewModel { BackupViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    var replaceSelected by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImport = uri
            replaceSelected = false
            showModeDialog = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.backup_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
        )
        if (state.working) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val errorText = appErrorText(state.error)
        if (errorText != null) {
            ErrorBanner(message = errorText, onDismiss = viewModel::dismiss)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.backup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            state.summary?.let { summary ->
                item {
                    ResultCard(summary = summary, imported = state.imported)
                }
            }

            item {
                SurfaceCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.FileDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.backup_export),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.backup_export_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                exportLauncher.launch("seenly-backup-${LocalDate.now()}.json")
                            },
                            enabled = !state.working,
                        ) {
                            Text(stringResource(R.string.backup_export))
                        }
                    }
                }
            }

            item {
                SurfaceCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.FileUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.backup_import),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.backup_import_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            enabled = !state.working,
                        ) {
                            Text(stringResource(R.string.backup_import))
                        }
                    }
                }
            }

            item {
                SurfaceCard {
                    Column {
                        Text(
                            text = stringResource(R.string.backup_about_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.backup_about_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.backup_credentials_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.backup_schema_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showModeDialog && pendingImport != null) {
        AlertDialog(
            onDismissRequest = {
                showModeDialog = false
                pendingImport = null
            },
            title = { Text(stringResource(R.string.backup_choose_mode)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !replaceSelected,
                            onClick = { replaceSelected = false },
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.backup_merge),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.backup_merge_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = replaceSelected,
                            onClick = { replaceSelected = true },
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.backup_replace),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.backup_replace_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (replaceSelected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.backup_replace_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingImport
                        if (uri != null) {
                            viewModel.import(
                                uri,
                                if (replaceSelected) ImportMode.REPLACE else ImportMode.MERGE,
                            )
                        }
                        showModeDialog = false
                        pendingImport = null
                    },
                ) {
                    Text(
                        stringResource(
                            if (replaceSelected) R.string.backup_replace else R.string.backup_merge,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showModeDialog = false
                        pendingImport = null
                    },
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ResultCard(summary: BackupSummary, imported: Boolean) {
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (imported) R.string.backup_import_success else R.string.backup_export_success,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                R.string.backup_summary_counts,
                summary.mediaCount,
                summary.episodeCount,
                summary.eventCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.backup_summary_lists,
                summary.listCount,
                summary.goalCount,
                summary.schemaVersion,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
