package org.torproject.android.ui.connect

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

import org.torproject.android.util.NetworkUtils
import org.torproject.jni.TorService

class ConnectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Off)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val _logState = MutableStateFlow("")
    val logState: StateFlow<String>  = _logState

    private val _eventChannel = Channel<ConnectEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun updateState(context: Context, status: String?) {
        val newState = when {
            !NetworkUtils.isNetworkAvailable(context, allowOtherVpnApps = true) -> ConnectUiState.NoInternet
            status == TorService.STATUS_STARTING -> ConnectUiState.Starting(null)
            status == TorService.STATUS_ON -> ConnectUiState.On
            status == TorService.STATUS_STOPPING -> ConnectUiState.Stopping
            else -> ConnectUiState.Off
        }
        _uiState.value = newState
    }

    fun updateLogState (logline: String) {
        _logState.value = logline
    }

    fun updateBootstrapPercent(percent: Int) {
        val currentState = _uiState.value
        if (currentState is ConnectUiState.Starting) {
            _uiState.value = currentState.copy(bootstrapPercent = percent)
        }
    }

    fun triggerStartTorAndVpn() {
        viewModelScope.launch {
            _eventChannel.send(ConnectEvent.StartTorAndVpn)
        }
    }
}

sealed class ConnectEvent {
    object StartTorAndVpn : ConnectEvent()
    object RefreshMenuList : ConnectEvent()
}
