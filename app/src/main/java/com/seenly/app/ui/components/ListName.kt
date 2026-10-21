package com.seenly.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seenly.app.R
import com.seenly.app.domain.model.MediaList
import com.seenly.app.domain.model.SmartList

/** Built-in list names are localized; custom names are user text. */
@Composable
fun mediaListName(list: MediaList): String = when (list.smartFilter) {
    SmartList.FAVORITES -> stringResource(R.string.list_favorites)
    SmartList.PLAN_TO_WATCH -> stringResource(R.string.list_plan_to_watch)
    null -> list.name
}
