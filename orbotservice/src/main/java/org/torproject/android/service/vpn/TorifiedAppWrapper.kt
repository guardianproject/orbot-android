package org.torproject.android.service.vpn

import kotlinx.serialization.Serializable

@Serializable
data class TorifiedAppWrapper(
    var header: String? = null,
    var subheader: String? = null,
    var app: TorifiedApp? = null,
)
