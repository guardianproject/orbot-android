package org.torproject.android.ui.connect

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

import org.torproject.android.service.TorStatus
import org.torproject.android.util.NetworkUtils

class ConnectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Off)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val _logState = MutableStateFlow<String>("")
    val logState: StateFlow<String>  = _logState

    private val _eventChannel = Channel<ConnectEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun updateState(context: Context, status: String?) {
        val torStatus = TorStatus.from(status)

        val newState = when {
            !NetworkUtils.isNetworkAvailable(context) -> ConnectUiState.NoInternet
            torStatus == TorStatus.STARTING -> ConnectUiState.Starting(null)
            torStatus == TorStatus.ON -> ConnectUiState.On
            torStatus == TorStatus.STOPPING -> ConnectUiState.Stopping
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
