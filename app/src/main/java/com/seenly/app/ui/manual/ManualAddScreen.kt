package com.seenly.app.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.seenly.app.domain.model.MediaType
import com.seenly.app.ui.components.ErrorBanner
import com.seenly.app.ui.appErrorText
import com.seenly.app.ui.components.DatePickerModal
import com.seenly.app.ui.components.mediaTypeLabel
import com.seenly.app.ui.formatMedium
import com.seenly.app.ui.seenlyViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ManualAddViewModel = seenlyViewModel { ManualAddViewModel(container) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val errorText = appErrorText(state.error)

    var title by rememberSaveable { mutableStateOf("") }
    var originalTitle by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(MediaType.MOVIE) }
    var releaseDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var seasons by rememberSaveable { mutableStateOf("") }
    var episodes by rememberSaveable { mutableStateOf("") }
    var runtime by rememberSaveable { mutableStateOf("") }
    var synopsis by rememberSaveable { mutableStateOf("") }
    var genres by rememberSaveable { mutableStateOf("") }
    var posterUrl by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.savedId) {
        state.savedId?.let(onSaved)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.manual_add_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    TextButton(
                        onClick = {
                            viewModel.save(
                                title = title,
                                type = type,
                                originalTitle = originalTitle,
                                releaseDate = releaseDate,
                                totalSeasons = seasons.toIntOrNull()?.takeIf { it > 0 },
                                totalEpisodes = episodes.toIntOrNull()?.takeIf { it > 0 },
                                runtimeMinutes = runtime.toIntOrNull()?.takeIf { it > 0 },
                                synopsis = synopsis,
                                posterUrl = posterUrl,
                                genres = genres.split(','),
                            )
                        },
                        enabled = title.isNotBlank(),
                    ) { Text(stringResource(R.string.save)) }
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (errorText != null) {
                ErrorBanner(message = errorText, onDismiss = viewModel::dismissError)
            }
            Text(
                text = stringResource(R.string.manual_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_title)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = originalTitle,
                onValueChange = { originalTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_original_title)) },
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.manual_type),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MediaType.entries.toList()) { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(mediaTypeLabel(option)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = releaseDate?.formatMedium()
                            ?: stringResource(R.string.manual_release_date),
                    )
                }
                if (releaseDate != null) {
                    TextButton(onClick = { releaseDate = null }) {
                        Text(stringResource(R.string.clear))
                    }
                }
            }
            if (type.hasEpisodes) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        value = seasons,
                        onValueChange = { seasons = it },
                        label = stringResource(R.string.manual_seasons),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = episodes,
                        onValueChange = { episodes = it },
                        label = stringResource(R.string.manual_episodes),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            NumberField(
                value = runtime,
                onValueChange = { runtime = it },
                label = stringResource(R.string.manual_runtime),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = genres,
                onValueChange = { genres = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_genres)) },
                placeholder = { Text(stringResource(R.string.manual_genres_hint)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = synopsis,
                onValueChange = { synopsis = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_synopsis)) },
                minLines = 3,
            )
            OutlinedTextField(
                value = posterUrl,
                onValueChange = { posterUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_poster_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.manual_offline_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initialDate = releaseDate,
            onConfirm = { date ->
                releaseDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(6)) },
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}
