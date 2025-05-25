package org.torproject.android.circumvention

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BuiltInBridgesResponse(
    @SerialName("meek-azure")
    val meek_azure: List<String>,
    val obfs4: List<String>,
    val snowflake: List<String>
)