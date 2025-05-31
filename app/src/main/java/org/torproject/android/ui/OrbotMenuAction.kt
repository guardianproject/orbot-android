package org.torproject.android.ui

data class OrbotMenuAction(
    val textId: Int,
    val imgId: Int,
    val removeTint: Boolean = false,
    var backgroundColor: Int? = null,
    val action: () -> Unit
)
