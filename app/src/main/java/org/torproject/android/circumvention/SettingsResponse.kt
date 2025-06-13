package org.torproject.android.circumvention

import kotlinx.serialization.Serializable

@Serializable data class SettingsResponse(val settings: List<Bridges>?, val errors: List<Error>?)
@Serializable data class Bridges(val bridges: Bridge)
@Serializable data class Bridge(val type: String, val source: String, val bridge_strings: List<String>? = null)
@Serializable data class Error(val code: Int, val detail: String)
