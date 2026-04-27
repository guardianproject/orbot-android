package org.torproject.android.ui.quicksettings

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import org.torproject.android.OrbotActivity

class LaunchOrbotTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, OrbotActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent =
            PendingIntentActivityWrapper(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT, false)
        TileServiceCompat.startActivityAndCollapse(this, pendingIntent)
    }
}