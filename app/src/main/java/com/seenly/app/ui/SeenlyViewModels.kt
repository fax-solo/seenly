package com.seenly.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Small helper so screens can construct their ViewModel from the AppContainer. */
@Composable
inline fun <reified VM : ViewModel> seenlyViewModel(
    key: String? = null,
    crossinline create: () -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer { create() }
    },
)
