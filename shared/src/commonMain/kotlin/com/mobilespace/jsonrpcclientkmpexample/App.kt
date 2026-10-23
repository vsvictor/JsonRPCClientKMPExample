package com.mobilespace.jsonrpcclientkmpexample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobilespace.jsonrpcclientkmpexample.di.AppContainer
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainScreen
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainViewModel

@Composable
fun App(viewModel: MainViewModel = viewModel { AppContainer.createMainViewModel() }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MaterialTheme {
        MainScreen(state = state, onAction = viewModel::onAction)
    }
}
