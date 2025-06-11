package org.torproject.android

sealed class ConnectUiState {
    object NoInternet : ConnectUiState()
    object Off : ConnectUiState()
    data class Starting(val bootstrapPercent: Int?) : ConnectUiState()
    object On : ConnectUiState()
    object Stopping : ConnectUiState()
}
