package org.torproject.android.service.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.torproject.android.service.OrbotService
import org.torproject.android.service.R
import org.torproject.android.service.util.Prefs

object Notifications {
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val appName = if (!Prefs.isCamoEnabled())
            context.getString(R.string.app_name)
        else
            Prefs.getCamoAppDisplayName()
        val channelDescription = if (!Prefs.isCamoEnabled())
            context.getString(R.string.app_description)
        else
            Prefs.getCamoAppDisplayName()
        manager.createNotificationChannel(
            NotificationChannel(
                OrbotService.NOTIFICATION_CHANNEL_ID,
                appName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
            description = channelDescription
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        })
    }

    @JvmStatic
    fun configureCamoNotification(notifyBuilder: NotificationCompat.Builder) {
        notifyBuilder
            .setContentTitle(Prefs.getCamoAppDisplayName())
            .setContentText(null)
            .setSubText(null)
            .setSmallIcon(R.drawable.ic_generic_info)
            .setProgress(0, 0, false)
            .setContentIntent(null)
    }
}