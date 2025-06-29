package org.torproject.android.ui.components

/**
 * Generic data class for a clickable menu item that has text and a graphic
 */
data class OrbotMenuAction(
    val textId: Int,
    val imgId: Int,
    val removeTint: Boolean = false,
    var backgroundColor: Int? = null,
    val action: () -> Unit
)