package org.torproject.android.ui.connect

import android.content.Context

import androidx.lifecycle.ViewModel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import org.torproject.android.util.NetworkUtils
import org.torproject.jni.TorService

class ConnectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Off)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val _logState = MutableStateFlow("")
    private val _subtitleState = MutableStateFlow("")
    val logState: StateFlow<String> = _logState
    val subtitleState: StateFlow<String> = _subtitleState

    fun updateState(context: Context, status: String?, progress: Int? = null) {
        val newState = when {
            status == TorService.STATUS_STARTING -> ConnectUiState.Starting(progress)
            status == TorService.STATUS_ON -> ConnectUiState.On
            status == TorService.STATUS_STOPPING -> ConnectUiState.Stopping
            !NetworkUtils.isNetworkAvailable(
                context,
                allowOtherVpnApps = true
            ) -> ConnectUiState.NoInternet

            else -> ConnectUiState.Off
        }
        _uiState.value = newState
    }

    fun updateLogState(logline: String) {
        _logState.value = logline
    }

    fun updateSubtitleState(subtitle: String = "") {
        _subtitleState.value = subtitle
    }


    fun updateBootstrapPercent(percent: Int) {
        val currentState = _uiState.value
        if (currentState is ConnectUiState.Starting) {
            _uiState.value = currentState.copy(bootstrapPercent = percent)
        }
    }
}
