package org.torproject.android.ui.connect

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

import org.torproject.android.service.util.NetworkUtils.isNetworkAvailable
import org.torproject.android.service.OrbotConstants

class ConnectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectUiState>(ConnectUiState.Off)
    val uiState: StateFlow<ConnectUiState> = _uiState

    private val _eventChannel = Channel<ConnectEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun updateState(context: Context, status: String?) {
        val newState = when {
            !isNetworkAvailable(context) -> ConnectUiState.NoInternet
            status == OrbotConstants.STATUS_STARTING -> ConnectUiState.Starting(null)
            status == OrbotConstants.STATUS_ON -> ConnectUiState.On
            status == OrbotConstants.STATUS_STOPPING -> ConnectUiState.Stopping
            else -> ConnectUiState.Off
        }
        _uiState.value = newState
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

    fun triggerRefreshMenuList() {
        viewModelScope.launch {
            _eventChannel.send(ConnectEvent.RefreshMenuList)
        }
    }

    fun triggerStopTorAndVpn() {
        viewModelScope.launch {
            _eventChannel.send(ConnectEvent.StopTorAndVpn)
        }
    }

    fun triggerSendNewnymSignal() {
        viewModelScope.launch {
            _eventChannel.send(ConnectEvent.SendNewnymSignal)
        }
    }
}

sealed class ConnectEvent {
    object StartTorAndVpn : ConnectEvent()
    object RefreshMenuList : ConnectEvent()
    object StopTorAndVpn : ConnectEvent()
    object SendNewnymSignal : ConnectEvent()
}
