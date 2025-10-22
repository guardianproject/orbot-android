package org.torproject.android.ui.more

import android.content.Context
import android.content.pm.PackageManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

import org.torproject.android.R
import org.torproject.android.service.OrbotService

class MoreViewModel : ViewModel() {
    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _eventChannel = Channel<MoreEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    fun updateStatus(context: Context, httpPort: Int, socksPort: Int) {
        val status = buildStatusString(context, httpPort, socksPort)
        _statusText.value = status
    }

    fun triggerEvent(event: MoreEvent) {
        viewModelScope.launch {
            _eventChannel.send(event)
        }
    }

    private fun buildStatusString(context: Context, httpPort: Int, socksPort: Int): String {
        val sb = StringBuilder()
        sb.append(context.getString(R.string.proxy_ports)).append(" ")

        if (httpPort != -1 && socksPort != -1) {
            sb.append("\nHTTP: $httpPort - SOCKS: $socksPort")
        } else {
            sb.append(": ").append(context.getString(R.string.ports_not_set))
        }

        sb.append("\n\n")

        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)

        sb.append("${context.getString(R.string.app_name)} ${info.versionName}\n")
        sb.append("Tor v${getTorVersion()}")

        return sb.toString()
    }

    private fun getTorVersion(): String {
        return OrbotService.BINARY_TOR_VERSION.split("-").toTypedArray()[0]
    }
}

sealed class MoreEvent {
    object OpenSettings : MoreEvent()
    object OpenVpnSettings : MoreEvent()
    object ChooseApps : MoreEvent()
    object OpenLog : MoreEvent()
    object OpenOnionServices : MoreEvent()
    object OpenClientAuth : MoreEvent()
    object OpenAbout : MoreEvent()
    object ExitApp : MoreEvent()
}
